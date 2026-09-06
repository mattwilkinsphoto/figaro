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
    private var initialized = true
    override def setSeed(seed: Long): Unit = {
      // java.util.Random invokes this override during superclass construction.
      val local = if (initialized) current.get() else null
      if (local == null) super.setSeed(seed) else local.setSeed(seed)
    }
    override protected def next(bits: Int): Int = {
      val local = current.get()
      if (local == null) super.next(bits) else local.nextInt() >>> (32 - bits)
    }
    override def nextDouble(): Double = {
      val local = current.get()
      if (local == null) super.nextDouble() else local.nextDouble()
    }
    override def nextInt(): Int = {
      val local = current.get()
      if (local == null) super.nextInt() else local.nextInt()
    }
    override def nextInt(bound: Int): Int = {
      val local = current.get()
      if (local == null) super.nextInt(bound) else local.nextInt(bound)
    }
    override def nextGaussian(): Double = {
      val local = current.get()
      if (local == null) super.nextGaussian() else local.nextGaussian()
    }
  }
}
