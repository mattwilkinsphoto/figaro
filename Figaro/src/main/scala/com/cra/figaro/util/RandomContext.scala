package com.cra.figaro.util

/** Opt-in synchronous RNG scopes. Threads do not inherit a scope. */
private[figaro] object RandomContext {
  private val current = new ThreadLocal[java.util.Random]

  def withRandom[A](random: java.util.Random)(body: => A): A = {
    val previous = current.get()
    current.set(random)
    try body
    finally { if (previous == null) current.remove() else current.set(previous) }
  }

  /** Keep util.random a stable Scala Random, including methods that call its Java delegate directly. */
  def global(seed: Long): scala.util.Random = new scala.util.Random(new RoutedRandom(seed))

  private class RoutedRandom(seed: Long) extends java.util.Random(seed) {
    private val fallback = SamplingRandom.seeded(seed)
    private def selected: java.util.Random = {
      val local = current.get()
      if (local == null) fallback else local
    }
    override def setSeed(seed: Long): Unit = {
      // java.util.Random invokes this override during superclass construction.
      if (fallback == null) super.setSeed(seed) else selected.setSeed(seed)
    }
    override protected def next(bits: Int): Int = {
      selected.nextInt() >>> (32 - bits)
    }
    override def nextDouble(): Double = {
      selected.nextDouble()
    }
    override def nextInt(): Int = {
      selected.nextInt()
    }
    override def nextInt(bound: Int): Int = {
      selected.nextInt(bound)
    }
    override def nextGaussian(): Double = {
      selected.nextGaussian()
    }
    override def nextLong(): Long = selected.nextLong()
    override def nextFloat(): Float = selected.nextFloat()
    override def nextBoolean(): Boolean = selected.nextBoolean()
    override def nextBytes(bytes: Array[Byte]): Unit = selected.nextBytes(bytes)
    override def nextExponential(): Double = selected.nextExponential()
  }
}
