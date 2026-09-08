package com.cra.figaro.util

import java.util.random.{RandomGenerator, RandomGeneratorFactory}

/** Versioned logical streams. Engine quality comes from the named providers;
  * allocation, consumption limits and replay are Figaro's responsibility.
  */
object RandomStreams {
  /** SeededV1 preserves historical seed expansion. PartitionedV1 uses native
    * Xoshiro jumps, native LXM splits, or disjoint Philox counter intervals.
    */
  enum Allocation { case SeededV1, PartitionedV1 }

  /** Allocation policy; maxRawDraws is enforced only by PartitionedV1.
    * @param allocation versioned stream assignment, SeededV1 by default
    * @param maxRawDraws positive per-stream limit on 64-bit engine words, not samples
    */
  final case class Config(allocation: Allocation = Allocation.SeededV1, maxRawDraws: Long = 1L << 48) {
    require(allocation != null && maxRawDraws > 0, "Allocation and positive raw-word budget required")
    /** Validate engine/policy compatibility before invoking any model callbacks.
      * @param algorithm named backend
      * @return Unit; throws IllegalArgumentException for unsupported combinations
      * @example `Config(Allocation.PartitionedV1).validate(SamplingRandom.Algorithm.Lxm)`
      */
    def validate(algorithm: SamplingRandom.Algorithm): Unit = {
      require(algorithm != null, "RNG algorithm required")
      require(allocation == Allocation.SeededV1 || Set(SamplingRandom.Algorithm.Lxm,
        SamplingRandom.Algorithm.Xoshiro256PlusPlus, SamplingRandom.Algorithm.Philox4x64).contains(algorithm),
        "PartitionedV1 supports LXM, Xoshiro256++ and Philox4x64 only")
    }
  }

  /** Replay identity for a stream at its START, not a mid-run checkpoint.
    * @param rootSeed root seed used for allocation
    * @param index nonnegative logical stream index (not a physical thread ID)
    * @param algorithm exact backend
    * @param config allocation version and raw-word budget
    * @param provider recorded provider/JDK provenance; replay rejects a mismatch
    */
  final case class Descriptor(rootSeed: Long, index: Int, algorithm: SamplingRandom.Algorithm,
    config: Config, provider: String) {
    require(index >= 0 && config != null && provider != null, "Invalid stream descriptor")
    config.validate(algorithm)
    /** Recreate the stream before its first draw. O(index) setup; never resume a consumed stream.
      * @return fresh Random; PartitionedV1 rejects reseeding and enforces the raw-word limit
      * @example `val fresh = result.chains.head.randomStream.get.replay()`
      */
    def replay(): java.util.Random = {
      require(provider == SamplingRandom.provenance(algorithm), "Replay provider/JDK mismatch")
      val cursor = new Cursor(rootSeed, algorithm, config)
      var i = 0
      while (i < index) { cursor.next(i); i += 1 }
      cursor.next(index).random
    }
  }

  /** Owned stream and immutable provenance. seed is the historical seed label;
    * for PartitionedV1 it cannot reconstruct the stream without its descriptor.
    */
  final class Stream private[RandomStreams] (val seed: Long, val descriptor: Descriptor,
    val random: java.util.Random)

  /** Fail closed before consuming a word beyond the reserved stream budget. */
  final class BudgetExceeded(val limit: Long)
    extends IllegalStateException(s"RNG raw-word budget exhausted ($limit)")

  /** Allocate in logical-index order, independently of scheduling or worker count.
    * @param rootSeed root seed; expansion adds no entropy
    * @param count nonnegative number of logical streams
    * @param algorithm named engine
    * @param config versioned policy; default preserves old sequences
    * @return index-ordered private generators and start-of-stream replay descriptors
    * @example `RandomStreams.allocate(42L, 4, SamplingRandom.Algorithm.Philox4x64, Config(Allocation.PartitionedV1))`
    */
  def allocate(rootSeed: Long, count: Int, algorithm: SamplingRandom.Algorithm,
    config: Config = Config()): Vector[Stream] = {
    require(count >= 0 && config != null, "Nonnegative stream count and config required")
    config.validate(algorithm)
    val cursor = new Cursor(rootSeed, algorithm, config)
    Vector.tabulate(count)(cursor.next)
  }

  private final class Cursor(root: Long, algorithm: SamplingRandom.Algorithm, config: Config) {
    private val labels = new java.util.SplittableRandom(root)
    private val provenance = SamplingRandom.provenance(algorithm)
    private val partitioned = config.allocation == Allocation.PartitionedV1
    private val jump = if (partitioned && algorithm == SamplingRandom.Algorithm.Xoshiro256PlusPlus)
      RandomGeneratorFactory.of[RandomGenerator.JumpableGenerator](algorithm.id).create(root) else null
    private val split = if (partitioned && algorithm == SamplingRandom.Algorithm.Lxm)
      RandomGeneratorFactory.of[RandomGenerator.SplittableGenerator](algorithm.id).create(root) else null
    // Deliberately use a separate expansion; keys are identical for all logical indices.
    private val keyExpansion = new java.util.SplittableRandom(root)
    private val key0 = keyExpansion.nextLong()
    private val key1 = keyExpansion.nextLong()
    def next(index: Int): Stream = {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("RNG allocation interrupted")
      val label = labels.nextLong()
      val rng = if (!partitioned) SamplingRandom.seeded(label, algorithm) else {
        val engine: RandomGenerator = algorithm match {
          case SamplingRandom.Algorithm.Xoshiro256PlusPlus => jump.copyAndJump()
          case SamplingRandom.Algorithm.Lxm => split.split()
          case SamplingRandom.Algorithm.Philox4x64 =>
            // 2^128 counter blocks (2^130 words) between indices. The Long budget
            // is strictly smaller; nonnegative Int indices cannot wrap this layout.
            // Apache increments before first output: first block is index * 2^128 + 1.
            val source = new org.apache.commons.rng.core.source64.Philox4x64(
              Array(key0, key1, 0L, 0L, index.toLong, 0L))
            new RandomGenerator { override def nextLong(): Long = source.nextLong() }
          case _ => throw new IllegalArgumentException("Unsupported partitioned engine")
        }
        new OwnedRandom(new RandomGenerator {
          private var remaining = config.maxRawDraws
          override def nextLong(): Long = {
            if (remaining == 0) throw new BudgetExceeded(config.maxRawDraws)
            remaining -= 1
            engine.nextLong()
          }
        })
      }
      new Stream(label, Descriptor(root, index, algorithm, config, provenance), rng)
    }
  }

  // Delegate every distribution to RandomGenerator defaults over counted nextLong.
  // A reseed would invalidate the partition; only construction may call setSeed.
  private final class OwnedRandom(engine: RandomGenerator) extends java.util.Random(0L) {
    private var ready = true
    override def setSeed(seed: Long): Unit = synchronized {
      if (ready) throw new UnsupportedOperationException("Replay the descriptor instead of reseeding an allocated stream")
      else super.setSeed(seed)
    }
    override protected def next(bits: Int): Int = synchronized { engine.nextInt() >>> (32 - bits) }
    override def nextLong(): Long = synchronized { engine.nextLong() }
    override def nextInt(): Int = synchronized { engine.nextInt() }
    override def nextInt(bound: Int): Int = synchronized { engine.nextInt(bound) }
    override def nextDouble(): Double = synchronized { engine.nextDouble() }
    override def nextFloat(): Float = synchronized { engine.nextFloat() }
    override def nextBoolean(): Boolean = synchronized { engine.nextBoolean() }
    override def nextBytes(bytes: Array[Byte]): Unit = synchronized { engine.nextBytes(bytes) }
    override def nextGaussian(): Double = synchronized { engine.nextGaussian() }
    override def nextExponential(): Double = synchronized { engine.nextExponential() }
  }
}
