package com.cra.figaro.test.modernization

import com.cra.figaro.util.{SamplingRandom as SR, random, withRandomSeed}
import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings as MH, ParImportance}
import com.cra.figaro.language.{Universe, Flip}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class SamplingRandomTest extends AnyWordSpec with Matchers {
  private def draws(r: java.util.random.RandomGenerator): Vector[Any] = {
    val bytes = new Array[Byte](19)
    r.nextBytes(bytes)
    Vector(bytes.toVector, r.nextLong(), r.nextInt(), r.nextInt(37), r.nextFloat(),
      r.nextDouble(), r.nextBoolean(), r.nextGaussian(), r.nextGaussian(),
      r.nextExponential(), r.nextLong(-100L, 12345L), r.nextDouble(-3, 7))
  }
  "Named scientific generators" should {
    "match the native JDK engines across primitive and nonuniform draws" in {
      for (algorithm <- Vector(SR.Algorithm.Lxm, SR.Algorithm.Xoshiro256PlusPlus, SR.Algorithm.LegacyJava)) {
        val expected = java.util.random.RandomGeneratorFactory.of[java.util.random.RandomGenerator](algorithm.id).create(991L)
        val actual = SR.seeded(991L, algorithm)
        for (_ <- 0 until 30) draws(actual) shouldBe draws(expected)
      }
    }
    "match Apache MT and PCG raw streams and their documented seed mapping" in {
      val mt = new org.apache.commons.math3.random.MersenneTwister(123L)
      val actualMt = SR.seeded(123L, SR.Algorithm.MersenneTwister)
      val split = new java.util.SplittableRandom(123L)
      val pcg = org.apache.commons.rng.simple.RandomSource.PCG_RXS_M_XS_64.create(Array(split.nextLong(), split.nextLong()))
      val actualPcg = SR.seeded(123L, SR.Algorithm.PcgRxsMXs64)
      for (_ <- 0 until 1000) {
        actualMt.nextLong() shouldBe mt.nextLong()
        actualMt.nextDouble() shouldBe mt.nextDouble()
        actualMt.nextGaussian() shouldBe mt.nextGaussian()
        actualPcg.nextLong() shouldBe pcg.nextLong()
        actualPcg.nextDouble() shouldBe pcg.nextDouble()
      }
    }
    "reseed every backend including Gaussian state and preserve explicit legacy replay" in {
      for (algorithm <- SR.Algorithm.values) {
        val rng = SR.seeded(7L, algorithm)
        draws(rng); rng.setSeed(81L)
        draws(rng) shouldBe draws(SR.seeded(81L, algorithm))
        SR.provenance(algorithm) should include (algorithm.id)
      }
      withRandomSeed(42L, SR.Algorithm.LegacyJava) {
        draws(random.self) shouldBe draws(new java.util.Random(42L))
      }
    }
    "restore differently named nested scopes and route all Scala delegate methods" in {
      for (algorithm <- SR.Algorithm.values) withRandomSeed(91L, algorithm) {
        val expected = SR.seeded(91L, algorithm)
        draws(random.self) shouldBe draws(expected)
        intercept[IllegalStateException] {
          withRandomSeed(33L, SR.Algorithm.MersenneTwister) {
            random.nextGaussian(); throw new IllegalStateException("test")
          }
        }
        draws(random.self) shouldBe draws(expected)
        random.setSeed(5L)
        draws(random.self) shouldBe draws(SR.seeded(5L, algorithm))
      }
    }
    "reject invalid configuration and retain primitive endpoint contracts" in {
      intercept[IllegalArgumentException](SR.seeded(1L, null))
      for (algorithm <- SR.Algorithm.values) {
        val r = SR.seeded(1L, algorithm)
        intercept[IllegalArgumentException](r.nextInt(0))
        val uniforms = Vector.fill(1000)(r.nextDouble())
        all(uniforms) should be >= 0.0
        all(uniforms) should be < 1.0
        r.ints(100, -17, 29).toArray.foreach { x => x should be >= -17; x should be < 29 }
        r.doubles(100).toArray.foreach { x => x should be >= 0.0; x should be < 1.0 }
      }
    }
    "retain chain-index replay across worker counts for every backend" in {
      for (algorithm <- SR.Algorithm.values) {
        val config = MC.Config(VS.Config(VS.Method.Quantile, draws=30, warmUp=5,
          randomAlgorithm=algorithm), chains=2, parallelism=1)
        def build(index: Int, seed: Long) = MC.Model(Vector(index.toDouble), x => -x.head*x.head/2)
        val serial = MC.run(config)(build)
        val parallel = MC.run(config.copy(parallelism=2))(build)
        serial.chains shouldBe parallel.chains
      }
    }
    "use explicit backends for graph MH chains and blocking importance workers" in {
      for (algorithm <- SR.Algorithm.values) {
        val c = MH.Config(chains=2, drawsPerChain=30, warmUp=5, parallelism=1, randomAlgorithm=algorithm)
        def build(u: Universe, index: Int) = MH.Model(Vector(
          MH.Observable("coin", Flip(.3)(using "", u))(x => if (x) 1.0 else 0.0)))
        val one = MH.run(c)(build)
        val two = MH.run(c.copy(parallelism=2))(build)
        one.chains.map(_.draws) shouldBe two.chains.map(_.draws)
        val observed = scala.collection.mutable.ArrayBuffer.empty[Double]
        val alg = ParImportance.seededWithAlgorithm(() => {
          val u = Universe.createNew()
          observed += random.nextDouble()
          Flip(.3)(using "query", u)
          u
        }, 2, 20, 719L, algorithm, "query")
        val split = new java.util.SplittableRandom(719L)
        observed.toVector shouldBe Vector.fill(2)(SR.seeded(split.nextLong(), algorithm).nextDouble())
        try alg.start() finally alg.kill()
      }
    }
  }
}
