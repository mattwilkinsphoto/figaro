package com.cra.figaro.test.modernization

import com.cra.figaro.util.{RandomStreams as RS, SamplingRandom as SR, RandomContext, random}
import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.{MultiChainVectorSliceSampler as MC, MultiChainMetropolisHastings as MH, ParImportance}
import com.cra.figaro.language.{Universe, Flip}
import java.util.random.{RandomGenerator as RG, RandomGeneratorFactory as RF}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class RandomStreamsTest extends AnyWordSpec with Matchers {
  private val supported = Vector(SR.Algorithm.Lxm, SR.Algorithm.Xoshiro256PlusPlus, SR.Algorithm.Philox4x64)
  private val partitioned = RS.Config(RS.Allocation.PartitionedV1)
  private def mixed(r: java.util.Random): Vector[Any] = {
    val bytes = new Array[Byte](17)
    r.nextBytes(bytes)
    Vector(r.nextLong(), r.nextInt(31), r.nextDouble(), r.nextFloat(), r.nextBoolean(),
      r.nextGaussian(), r.nextExponential(), bytes.toVector, r.nextLong(-500, 800),
      r.nextDouble(-10, 5), r.ints(3, -10, 15).toArray.toVector)
  }
  private def hex(value: String): Long = java.lang.Long.parseUnsignedLong(value, 16)

  "Versioned RNG streams" should {
    "preserve every historical engine and child seed sequence under SeededV1" in {
      for (a <- SR.Algorithm.values; root <- Vector(0L, -1L, Long.MinValue, 42L)) {
        val seeds = new java.util.SplittableRandom(root)
        RS.allocate(root, 4, a).foreach { stream =>
          val seed = seeds.nextLong()
          stream.seed shouldBe seed
          val expected = SR.seeded(seed, a)
          for (_ <- 0 until 10) mixed(stream.random) shouldBe mixed(expected)
          mixed(stream.descriptor.replay()) shouldBe mixed(SR.seeded(seed, a))
        }
      }
    }
    "match published Random123 Philox4x64-10 known-answer blocks" in {
      // Numerical test vectors, not a copied generator implementation:
      // https://github.com/DEShawResearch/random123/blob/main/tests/kat_vectors
      val vectors = Vector(
        (Vector.fill(4)(0L), Vector.fill(2)(0L),
          "16554d9eca36314c db20fe9d672d0fdc d7e772cee186176b 7e68b68aec7ba23b"),
        (Vector.fill(4)(-1L), Vector.fill(2)(-1L),
          "87b092c3013fe90b 438c3c67be8d0224 9cc7d7c69cd777b6 a09caebf594f0ba0"),
        ("243f6a8885a308d3 13198a2e03707344 a4093822299f31d0 082efa98ec4e6c89".split(" ").map(hex).toVector,
          "452821e638d01377 be5466cf34e90c6c".split(" ").map(hex).toVector,
          "a528f45403e61d95 38c72dbd566e9788 a5a1610e72fd18b5 57bd43b5e52b7fe6"))
      vectors.foreach { (counter, key, answer) =>
        // Apache increments before emitting, whereas Random123 vectors address
        // the block itself. Subtract one with little-endian borrow, including wrap.
        val before = counter.toArray
        var j = 0
        while (j < before.length && before(j) == 0L) { before(j) = -1L; j += 1 }
        if (j < before.length) before(j) -= 1L
        val native = new org.apache.commons.rng.core.source64.Philox4x64(key.toArray ++ before)
        Vector.fill(4)(native.nextLong()) shouldBe answer.split(" ").map(hex).toVector
      }
    }
    "match native split, jump and counter-layout streams without changing their raw words" in {
      val root = 1009L
      val jump = RF.of[RG.JumpableGenerator](SR.Algorithm.Xoshiro256PlusPlus.id).create(root)
      jump.jumpDistance() shouldBe math.pow(2.0, 128)
      val split = RF.of[RG.SplittableGenerator](SR.Algorithm.Lxm.id).create(root)
      val expansion = new java.util.SplittableRandom(root)
      val keys = Array(expansion.nextLong(), expansion.nextLong())
      for (a <- supported) RS.allocate(root, 4, a, partitioned).zipWithIndex.foreach { (stream, index) =>
        val expected: RG = a match {
          case SR.Algorithm.Lxm => split.split()
          case SR.Algorithm.Xoshiro256PlusPlus => jump.copyAndJump()
          case _ =>
            val p = new org.apache.commons.rng.core.source64.Philox4x64(Array(keys(0), keys(1), 0L, 0L, index.toLong, 0L))
            new RG { override def nextLong(): Long = p.nextLong() }
        }
        Vector.fill(1000)(stream.random.nextLong()) shouldBe Vector.fill(1000)(expected.nextLong())
      }
      val native = new org.apache.commons.rng.core.source64.Philox4x64(Array(keys(0), keys(1), 0L, 0L, 0L, 0L))
      val seeded = SR.seeded(root, SR.Algorithm.Philox4x64)
      Vector.fill(1000)(seeded.nextLong()) shouldBe Vector.fill(1000)(native.nextLong())
    }
    "retain prefixes and replay mixed draws without depending on total stream count" in {
      for (a <- supported) {
        val short = RS.allocate(33L, 2, a, partitioned)
        val long = RS.allocate(33L, 5, a, partitioned)
        short.zip(long).foreach { (s, l) =>
          s.descriptor shouldBe l.descriptor
          val replay = s.descriptor.replay()
          for (_ <- 0 until 20) {
            val expected = mixed(s.random)
            mixed(l.random) shouldBe expected
            mixed(replay) shouldBe expected
          }
        }
      }
    }
    "fail closed on raw-word exhaustion across primitive and distribution APIs" in {
      for (a <- supported) {
        val r = RS.allocate(5L, 1, a, RS.Config(RS.Allocation.PartitionedV1, 3)).head.random
        r.nextBoolean(); r.nextInt(); r.nextDouble()
        intercept[RS.BudgetExceeded](r.nextLong()).limit shouldBe 3L
        intercept[RS.BudgetExceeded](r.nextGaussian())
        intercept[RS.BudgetExceeded](r.nextExponential())
        intercept[RS.BudgetExceeded](r.nextBytes(new Array[Byte](1)))
        intercept[RS.BudgetExceeded](r.longs(1).toArray)
        intercept[RS.BudgetExceeded](r.nextFloat())
        intercept[UnsupportedOperationException](r.setSeed(17L))
        intercept[RS.BudgetExceeded](r.nextLong())
        val invalid = RS.allocate(5L, 1, a, RS.Config(RS.Allocation.PartitionedV1, 1)).head.random
        intercept[IllegalArgumentException](invalid.nextInt(0))
        invalid.nextLong() // Invalid bounds did not consume the sole word.
        intercept[RS.BudgetExceeded](invalid.nextLong())
      }
    }
    "validate replay identity and unsupported policies before allocation or model callbacks" in {
      intercept[IllegalArgumentException](RS.Config(maxRawDraws=0))
      intercept[IllegalArgumentException](RS.allocate(1L, -1, SR.Algorithm.Lxm))
      RS.allocate(1L, 0, SR.Algorithm.Lxm) shouldBe Vector.empty
      for (a <- Vector(SR.Algorithm.LegacyJava, SR.Algorithm.MersenneTwister, SR.Algorithm.PcgRxsMXs64)) {
        intercept[IllegalArgumentException](RS.allocate(1L, 0, a, partitioned))
        intercept[IllegalArgumentException](MH.Config(randomAlgorithm=a, randomStreams=partitioned))
        intercept[IllegalArgumentException](MC.Config(VS.Config(VS.Method.Quantile, randomAlgorithm=a), randomStreams=partitioned))
      }
      val d = RS.allocate(1L, 1, SR.Algorithm.Lxm, partitioned).head.descriptor
      intercept[IllegalArgumentException](d.copy(provider="different runtime").replay())
      intercept[IllegalArgumentException](d.copy(index = -1))
    }
    "produce identical MH and vector traces across worker counts under every partitioned backend" in {
      for (a <- supported) {
        val c = MC.Config(VS.Config(VS.Method.Quantile, draws=40, warmUp=5, randomAlgorithm=a),
          chains=3, parallelism=1, randomStreams=partitioned)
        def model(i: Int, seed: Long) = MC.Model(Vector(i.toDouble), x => -x.head*x.head/2)
        val one = MC.run(c)(model)
        val many = MC.run(c.copy(parallelism=3))(model)
        one.chains shouldBe many.chains
        one.chains.foreach { chain =>
          VS.runWithRandom(c.sampler, Vector(chain.index.toDouble), chain.randomStream.get.replay())(
            x => -x.head*x.head/2) shouldBe chain.result
        }
        val mh = MH.Config(chains=3, drawsPerChain=40, warmUp=5, parallelism=1,
          randomAlgorithm=a, randomStreams=partitioned)
        // Explicit proposal removes legacy hash-bucket rejection order from this
        // test of RNG ownership. Arbitrary default-proposal graphs are not replay-certified.
        def build(u: Universe, i: Int) = {
          val coin = Flip(.3)(using "", u)
          MH.Model(Vector(MH.Observable("coin", coin)(b => if (b) 1.0 else 0.0)),
            Some(com.cra.figaro.algorithm.sampling.FinalScheme(() => coin)))
        }
        val first = MH.run(mh)(build)
        val second = MH.run(mh.copy(parallelism=3))(build)
        first.chains.map(c => (c.draws, c.randomStream)) shouldBe second.chains.map(c => (c.draws, c.randomStream))
      }
    }
    "retain a worker stream across importance model construction and sampling and expose its identity" in {
      for (a <- supported) {
        val seen = scala.collection.mutable.ArrayBuffer.empty[Long]
        val alg = ParImportance.seededWithStreams(() => {
          seen += random.nextLong()
          val u = Universe.createNew()
          Flip(.3)(using "query", u)
          u
        }, 3, 120, 419L, a, partitioned, "query")
        seen.toVector shouldBe alg.randomStreams.map(_.replay().nextLong())
        alg.randomStreams.map(_.index) shouldBe Vector(0, 1, 2)
        try alg.start() finally alg.kill()
      }
    }
    "propagate exhaustion without returning partial successful chains and clean up workers" in {
      val limit = RS.Config(RS.Allocation.PartitionedV1, 1)
      val c = MC.Config(VS.Config(VS.Method.Quantile, draws=40), chains=2, randomStreams=limit)
      intercept[MC.ChainFailure] {
        MC.run(c)((i, seed) => MC.Model(Vector(1.0), x => -x.head*x.head/2))
      }.getCause shouldBe a[RS.BudgetExceeded]
      val mh = MH.Config(chains=2, drawsPerChain=40, randomStreams=limit)
      intercept[MH.ChainFailure] {
        MH.run(mh)((u, i) => MH.Model(Vector(MH.Observable("x", Flip(.3)(using "", u))(b => if (b) 1.0 else 0.0))))
      }.getCause shouldBe a[RS.BudgetExceeded]
    }
    "preserve interruption and restore an outer RNG after an exhausted inner scope" in {
      val outer = SR.seeded(1L)
      val expected = SR.seeded(1L)
      RandomContext.withRandom(outer) {
        val inner = RS.allocate(1L, 1, SR.Algorithm.Lxm, RS.Config(RS.Allocation.PartitionedV1, 1)).head.random
        intercept[RS.BudgetExceeded](RandomContext.withRandom(inner) { random.nextLong(); random.nextLong() })
        random.nextLong() shouldBe expected.nextLong()
      }
      Thread.currentThread().interrupt()
      try {
        intercept[InterruptedException](RS.allocate(1L, 1, SR.Algorithm.Lxm, partitioned))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }
  }
}
