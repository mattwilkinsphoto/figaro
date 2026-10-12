package com.cra.figaro.library.atomic.discrete

import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.DistributionNumerics as N

/** Explicit finite enumeration into Figaro's existing exact Select/factor backend.
  * No tail truncation and no silent switch to sampled ranges on budget exhaustion.
  */
object FiniteCountInference {
  /** Enumeration budget, not a bound on all intermediate factors in a larger graph.
    * @param maxStates maximum enumerated states, 1..100000
    * @param maxCells maximum scalar entries in the enumerated states, 1..10000000
    */
  final case class Budget(maxStates: Int = 10000, maxCells: Int = 1000000) {
    require(maxStates > 0 && maxStates <= 100000 && maxCells > 0 && maxCells <= 10000000,
      "Invalid finite enumeration budget")
    private[discrete] def check(states: Long, dimension: Int): Unit =
      require(states <= maxStates && states * dimension.toLong <= maxCells,
        "Finite support exceeds the explicit enumeration budget; use sampling or a larger reviewed budget")
  }

  private def mass(log: Double): Double = {
    N.check()
    require(!log.isNaN && log != Double.PositiveInfinity, "Invalid count log mass")
    val p = math.exp(log)
    if (!p.isFinite || (p == 0.0 && log.isFinite))
      throw new ArithmeticException("Finite factor mass is not representable; no tail mass was discarded")
    p
  }

  private def element[T](table: Vector[(Double, T)], name: Name[T], collection: ElementCollection): AtomicSelect[T] = {
    val total = table.map(_._1).sum
    require(total.isFinite && math.abs(total - 1.0) <= 1e-8,
      "Enumerated count probabilities do not normalize within 1e-8")
    new AtomicSelect(name, table.filter(_._1 > 0.0).toList, collection)
  }

  /** Enumerate a finite count law (including mixtures of finite laws) before registration.
    * @param law immutable count kernel with a finite upper support bound
    * @param budget explicit state/storage limits
    * @param name contextual name
    * @param collection owning collection
    * @return exact-support Select element suitable for variable elimination; probabilities use Double arithmetic
    * @example `FiniteCountInference.count(HypergeometricDistribution(20,7,5))`
    */
  def count(law: CountDistribution, budget: Budget = Budget())
    (using name: Name[Int], collection: ElementCollection): AtomicSelect[Int] = {
    require(law != null && budget != null)
    N.check()
    val (lower, upper) = law.support
    require(upper.nonEmpty, "Exact count inference requires finite support; unbounded tails are not truncated")
    val last = upper.get
    require(last >= lower, "Invalid count support")
    budget.check(last.toLong - lower.toLong + 1L, 1)
    val table = (lower.toLong to last.toLong).iterator.map { k =>
      (mass(law.logProbability(k.toInt)), k.toInt)
    }.toVector
    element(table, name, collection)
  }

  /** Enumerate the complete joint multinomial support, excluding impossible zero-probability categories.
    * @param law fixed multinomial law
    * @param budget preflight limits, checked before constructing any state vectors or registering an element
    * @param name contextual name
    * @param collection owning collection
    * @return exact-support Select on count vectors; not an independent-marginals approximation
    * @example `FiniteCountInference.multinomial(MultinomialDistribution(4,Vector(.2,.3,.5)))`
    */
  def multinomial(law: MultinomialDistribution, budget: Budget = Budget())
    (using name: Name[Vector[Int]], collection: ElementCollection): AtomicSelect[Vector[Int]] = {
    require(law != null && budget != null)
    N.check()
    val active = law.normalizedProbabilities.indices.filter(law.normalizedProbabilities(_) > 0.0).toVector
    // Number of weak compositions: choose(n + k - 1, k - 1). Stop preflight early.
    var count = BigInt(1)
    for (i <- 1 until active.size) {
      count = count * (law.trials.toLong + i) / i
      require(count <= budget.maxStates, "Multinomial support exceeds the explicit enumeration budget")
    }
    budget.check(count.toLong, law.dimension)
    val buffer = Vector.newBuilder[(Double, Vector[Int])]
    val counts = Array.fill(law.dimension)(0)
    def enumerate(index: Int, remaining: Int): Unit = {
      N.check()
      if (index == active.size - 1) {
        counts(active(index)) = remaining
        val state = counts.toVector
        buffer += ((mass(law.logProbability(state)), state))
      } else {
        var k = 0
        while (k <= remaining) {
          counts(active(index)) = k
          enumerate(index + 1, remaining - k)
          k += 1
        }
      }
    }
    enumerate(0, law.trials)
    element(buffer.result(), name, collection)
  }
}
