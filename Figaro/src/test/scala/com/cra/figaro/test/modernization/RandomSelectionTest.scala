package com.cra.figaro.test.modernization

import com.cra.figaro.util.{RandomSelection as Select, RandomStreams as RS, SamplingRandom as SR, random}
import com.cra.figaro.algorithm.sampling.{VectorSliceSampler as VS, FinalScheme}
import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings as MH, MultiChainVectorSliceSampler as MC}
import com.cra.figaro.language.{Universe, Flip}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class RandomSelectionTest extends AnyWordSpec with Matchers {
  import Select.{Purpose, Preset, Choice, Requirements}

  "Purpose-based RNG selection" should {
    "pin every V1 pair and preserve existing defaults without consuming random draws" in {
      val expected = Vector(
        (Purpose.GeneralInference, SR.Algorithm.Lxm, RS.Allocation.SeededV1),
        (Purpose.FixedChains, SR.Algorithm.Xoshiro256PlusPlus, RS.Allocation.PartitionedV1),
        (Purpose.CounterRanges, SR.Algorithm.Philox4x64, RS.Allocation.PartitionedV1))
      com.cra.figaro.util.withRandomSeed(42L) {
        val control = SR.scalaRandom(42L)
        expected.foreach { (purpose, algorithm, allocation) =>
          val d = Select.resolve(purpose)
          d.preset shouldBe Preset.V1
          d.purpose shouldBe purpose
          d.algorithm shouldBe algorithm
          d.streams shouldBe RS.Config(allocation)
          d.requirements shouldBe Requirements()
          d.overridden shouldBe false
          d.reason.nonEmpty shouldBe true
          d.provider shouldBe SR.provenance(algorithm)
        }
        random.nextLong() shouldBe control.nextLong()
      }
      SR.defaultAlgorithm shouldBe SR.Algorithm.Lxm
      MH.Config().randomStreams shouldBe RS.Config()
    }
    "apply explicit pairs but never silently downgrade purpose or hard requirements" in {
      val d = Select.resolve(Purpose.FixedChains,
        explicit=Some(Choice(SR.Algorithm.Lxm, RS.Allocation.PartitionedV1)), maxRawDraws=99)
      d.overridden shouldBe true
      d.algorithm shouldBe SR.Algorithm.Lxm
      d.streams.maxRawDraws shouldBe 99L
      d.reason should include ("Explicit")
      val disjoint = Requirements(disjointIntervals=true)
      for (a <- Vector(SR.Algorithm.Xoshiro256PlusPlus, SR.Algorithm.Philox4x64)) {
        Select.resolve(Purpose.FixedChains, explicit=Some(Choice(a, RS.Allocation.PartitionedV1)),
          requirements=disjoint).requirements shouldBe disjoint
      }
      intercept[IllegalArgumentException](Select.resolve(Purpose.FixedChains,
        explicit=Some(Choice(SR.Algorithm.Lxm, RS.Allocation.PartitionedV1)), requirements=disjoint))
      intercept[IllegalArgumentException](Select.resolve(Purpose.GeneralInference, requirements=disjoint))
      intercept[IllegalArgumentException](Select.resolve(Purpose.CounterRanges,
        explicit=Some(Choice(SR.Algorithm.Xoshiro256PlusPlus, RS.Allocation.PartitionedV1))))
      intercept[IllegalArgumentException](Select.resolve(Purpose.FixedChains,
        explicit=Some(Choice(SR.Algorithm.Xoshiro256PlusPlus, RS.Allocation.SeededV1))))
      for (a <- Vector(SR.Algorithm.LegacyJava, SR.Algorithm.MersenneTwister, SR.Algorithm.PcgRxsMXs64)) {
        Select.resolve(Purpose.GeneralInference, explicit=Some(Choice(a, RS.Allocation.SeededV1))).algorithm shouldBe a
        intercept[IllegalArgumentException](Select.resolve(Purpose.FixedChains,
          explicit=Some(Choice(a, RS.Allocation.PartitionedV1))))
      }
    }
    "reject unsupported sample addressing and malformed requests before any model can run" in {
      for (p <- Purpose.values)
        intercept[IllegalArgumentException](Select.resolve(p, requirements=Requirements(perSampleAddressing=true)))
      intercept[IllegalArgumentException](Select.resolve(null))
      intercept[IllegalArgumentException](Select.resolve(Purpose.GeneralInference, preset=null))
      intercept[IllegalArgumentException](Select.resolve(Purpose.GeneralInference, explicit=null))
      intercept[IllegalArgumentException](Select.resolve(Purpose.GeneralInference, explicit=Some(null)))
      intercept[IllegalArgumentException](Select.resolve(Purpose.GeneralInference, requirements=null))
      for (n <- Vector(0L, -1L, Long.MinValue))
        intercept[IllegalArgumentException](Select.resolve(Purpose.GeneralInference, maxRawDraws=n))
      intercept[IllegalArgumentException](Choice(null, RS.Allocation.SeededV1))
      intercept[IllegalArgumentException](Choice(SR.Algorithm.Lxm, null))
      val d = Select.resolve(Purpose.CounterRanges)
      intercept[IllegalArgumentException](d.configure(null: MH.Config))
      intercept[IllegalArgumentException](d.configure(null: MC.Config))
      intercept[IllegalArgumentException](d.importance(null, 1, 1, 42L, "coin"))
    }
    "match direct allocation and replay under every preset and preserve seeded compatibility" in {
      for (p <- Purpose.values; root <- Vector(0L, 42L, Long.MinValue)) {
        val d = Select.resolve(p)
        val selected = d.allocate(root, 3)
        val direct = RS.allocate(root, 3, d.algorithm, d.streams)
        selected.zip(direct).foreach { (s, expected) =>
          s.descriptor shouldBe expected.descriptor
          val replay = s.descriptor.replay() // Replay bypasses automatic selection.
          val draws = Vector.fill(100)(s.random.nextGaussian())
          draws shouldBe Vector.fill(100)(expected.random.nextGaussian())
          draws shouldBe Vector.fill(100)(replay.nextGaussian())
        }
        d.allocate(root, 0) shouldBe empty
        intercept[IllegalArgumentException](d.allocate(root, -1))
      }
    }
    "enforce raw-word budgets without affecting seeded compatibility policy" in {
      for (p <- Vector(Purpose.FixedChains, Purpose.CounterRanges)) {
        val stream = Select.resolve(p, maxRawDraws=1).allocate(42L, 1).head.random
        stream.nextLong()
        intercept[RS.BudgetExceeded](stream.nextLong()).limit shouldBe 1L
        intercept[UnsupportedOperationException](stream.setSeed(42L))
      }
      val legacyPolicy = Select.resolve(Purpose.GeneralInference, maxRawDraws=1).allocate(42L, 1).head.random
      Vector.fill(10)(legacyPolicy.nextLong()).size shouldBe 10
    }
    "configure both runners without changing work settings and match direct explicit traces" in {
      for (p <- Purpose.values) {
        val d = Select.resolve(p)
        val vc = MC.Config(VS.Config(VS.Method.Quantile, draws=20, warmUp=3, seed=712L),
          chains=3, parallelism=1, maxStoredValues=1000L)
        val selected = d.configure(vc)
        val explicit = vc.copy(sampler=vc.sampler.copy(randomAlgorithm=d.algorithm), randomStreams=d.streams)
        selected shouldBe explicit
        def model(i: Int, label: Long) = MC.Model(Vector(i.toDouble), x => -x.head*x.head/2)
        MC.run(selected)(model).chains shouldBe MC.run(explicit.copy(parallelism=3))(model).chains
        val mh = MH.Config(chains=3, drawsPerChain=20, warmUp=3, parallelism=1, seed=713L)
        d.configure(mh) shouldBe mh.copy(randomAlgorithm=d.algorithm, randomStreams=d.streams)
        def build(u: Universe, i: Int) = {
          val coin = Flip(.3)(using "coin", u)
          MH.Model(Vector(MH.Observable("coin", coin)(b => if (b) 1.0 else 0.0)), Some(FinalScheme(() => coin)))
        }
        val one = MH.run(d.configure(mh))(build)
        val many = MH.run(d.configure(mh.copy(parallelism=3)))(build)
        one.chains.map(_.draws) shouldBe many.chains.map(_.draws)
        one.chains.flatMap(_.randomStream).map(_.algorithm).distinct shouldBe Vector(d.algorithm)
      }
    }
    "configure blocking importance before construction and expose the exact selected descriptors" in {
      for (p <- Vector(Purpose.GeneralInference, Purpose.CounterRanges)) {
        val d = Select.resolve(p)
        val seen = scala.collection.mutable.ArrayBuffer.empty[Long]
        val alg = d.importance(() => {
          seen += random.nextLong()
          val u = new Universe
          Flip(.3)(using "coin", u)
          u
        }, 3, 120, 42L, "coin")
        try {
          seen.toVector shouldBe alg.randomStreams.map(_.replay().nextLong())
          alg.randomStreams.map(_.algorithm).distinct shouldBe Vector(d.algorithm)
          alg.start()
          val probability = alg.probability("coin", true)
          probability should (be >= 0.0 and be <= 1.0)
        } finally alg.kill()
      }
    }
  }
}
