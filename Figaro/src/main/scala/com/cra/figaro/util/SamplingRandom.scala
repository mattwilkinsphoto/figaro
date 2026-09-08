package com.cra.figaro.util

import java.util.random.{RandomGenerator, RandomGeneratorFactory}

/** Named scientific RNG backends. Uses JDK/Apache implementations, not a Figaro PRNG algorithm. */
object SamplingRandom {
  /** LXM is the production default. LegacyJava is only for explicit historical replay. */
  enum Algorithm(val id: String) {
    case Lxm extends Algorithm("L64X128MixRandom")
    case Xoshiro256PlusPlus extends Algorithm("Xoshiro256PlusPlus")
    case PcgRxsMXs64 extends Algorithm("PCG_RXS_M_XS_64")
    case MersenneTwister extends Algorithm("MT19937")
    case LegacyJava extends Algorithm("Random")
    case Philox4x64 extends Algorithm("PHILOX_4X64")
  }

  /** Default for Figaro-owned sampling streams. Record this name alongside the seed. */
  val defaultAlgorithm: Algorithm = Algorithm.Lxm

  /** Create an owned, reseedable Java/Scala-compatible generator.
    * @param seed reproducible seed for the explicitly named JDK algorithm
    * @param algorithm LXM by default; LegacyJava reproduces java.util.Random method sequences
    * @return fresh generator; prefer one instance per logical worker/chain
    * @example `val rng = SamplingRandom.seeded(42L)`
    */
  def seeded(seed: Long, algorithm: Algorithm = defaultAlgorithm): java.util.Random = {
    require(algorithm != null, "RNG algorithm is required")
    algorithm match {
      case Algorithm.LegacyJava => new java.util.Random(seed)
      case _ => new JdkAdapter(seed, algorithm)
    }
  }

  /** Create a Scala Random for explicit distribution-kernel calls.
    * @param seed reproducible seed
    * @param algorithm named backend, LXM by default
    * @return fresh Scala wrapper owning its generator
    * @example `val rng = SamplingRandom.scalaRandom(42L)`
    */
  def scalaRandom(seed: Long, algorithm: Algorithm = defaultAlgorithm): scala.util.Random =
    new scala.util.Random(seeded(seed, algorithm))

  /** Describe the backend implementation for experiment provenance.
    * @param algorithm selected backend
    * @return algorithm, provider/version and JVM runtime description (not a serialized RNG state)
    * @example `SamplingRandom.provenance(SamplingRandom.defaultAlgorithm)`
    */
  def provenance(algorithm: Algorithm): String = {
    require(algorithm != null, "RNG algorithm is required")
    val provider = algorithm match {
      case Algorithm.PcgRxsMXs64 => "Apache Commons RNG 1.7; SplitMix64-to-native-seed-v1"
      case Algorithm.MersenneTwister => "Apache Commons Math 3.6.1; long-seed"
      case Algorithm.Philox4x64 => "Apache Commons RNG 1.7; Philox4x64-10; SplitMix64-key-v1"
      case _ => "JDK"
    }
    s"${algorithm.id}; $provider; ${System.getProperty("java.vendor")} ${System.getProperty("java.runtime.version")}"
  }

  // Random invokes setSeed from its constructor, before subclass fields are initialized.
  // Recreating the JDK engine also resets any nonuniform-generator state on reseeding.
  // Synchronization retains Random's per-call thread safety for the shared fallback;
  // it does not make a Figaro model or a multi-call random computation thread-safe.
  private final class JdkAdapter(seed: Long, algorithm: Algorithm) extends java.util.Random(0L) {
    private var engine: RandomGenerator = create(seed)
    private def create(value: Long): RandomGenerator = algorithm match {
      case Algorithm.Philox4x64 =>
        val expansion = new java.util.SplittableRandom(value)
        val source = new org.apache.commons.rng.core.source64.Philox4x64(
          Array(expansion.nextLong(), expansion.nextLong(), 0L, 0L, 0L, 0L))
        new RandomGenerator { override def nextLong(): Long = source.nextLong() }
      case Algorithm.PcgRxsMXs64 =>
        // Supply the provider's native seed shape, with a pinned, explicit expansion.
        val expansion = new java.util.SplittableRandom(value)
        val source = org.apache.commons.rng.simple.RandomSource.PCG_RXS_M_XS_64.create(
          Array(expansion.nextLong(), expansion.nextLong()))
        new RandomGenerator {
          override def nextLong(): Long = source.nextLong()
          override def nextInt(): Int = source.nextInt()
          override def nextInt(bound: Int): Int = source.nextInt(bound)
          override def nextDouble(): Double = source.nextDouble()
          override def nextFloat(): Float = source.nextFloat()
          override def nextBoolean(): Boolean = source.nextBoolean()
          override def nextBytes(bytes: Array[Byte]): Unit = source.nextBytes(bytes)
        }
      case Algorithm.MersenneTwister =>
        val source = new org.apache.commons.math3.random.MersenneTwister(value)
        new RandomGenerator {
          override def nextLong(): Long = source.nextLong()
          override def nextInt(): Int = source.nextInt()
          override def nextInt(bound: Int): Int = source.nextInt(bound)
          override def nextDouble(): Double = source.nextDouble()
          override def nextFloat(): Float = source.nextFloat()
          override def nextBoolean(): Boolean = source.nextBoolean()
          override def nextBytes(bytes: Array[Byte]): Unit = source.nextBytes(bytes)
          override def nextGaussian(): Double = source.nextGaussian()
        }
      case _ => RandomGeneratorFactory.of[RandomGenerator](algorithm.id).create(value)
    }
    override def setSeed(value: Long): Unit = synchronized {
      if (engine == null) super.setSeed(value) else engine = create(value)
    }
    override protected def next(bits: Int): Int = synchronized { engine.nextInt() >>> (32-bits) }
    override def nextInt(): Int = synchronized { engine.nextInt() }
    override def nextInt(bound: Int): Int = synchronized { engine.nextInt(bound) }
    override def nextLong(): Long = synchronized { engine.nextLong() }
    override def nextDouble(): Double = synchronized { engine.nextDouble() }
    override def nextFloat(): Float = synchronized { engine.nextFloat() }
    override def nextBoolean(): Boolean = synchronized { engine.nextBoolean() }
    override def nextBytes(bytes: Array[Byte]): Unit = synchronized { engine.nextBytes(bytes) }
    override def nextGaussian(): Double = synchronized { engine.nextGaussian() }
    override def nextExponential(): Double = synchronized { engine.nextExponential() }
  }
}
