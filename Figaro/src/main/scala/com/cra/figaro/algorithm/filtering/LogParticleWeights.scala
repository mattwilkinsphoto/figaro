package com.cra.figaro.algorithm.filtering

/** Internal log-domain multinomial sampling (INF-13). No probability floors or CDF.
  * This is floating-point arithmetic, not an exact-real or missing-mode guarantee.
  */
private[figaro] object LogParticleWeights {
  final case class Normalized(logs: Vector[Double], weights: Vector[Double],
    peak: Double, logSum: Double)

  def normalize(logs: Vector[Double]): Normalized = {
    require(logs.nonEmpty && logs.forall(l => l.isFinite || l == Double.NegativeInfinity))
    val peak = logs.max
    require(peak.isFinite, "At least one positive log weight required")
    val shifted = logs.map(l => {
      val d = l-peak
      if (l.isFinite && !d.isFinite) throw new ArithmeticException("Log weight range not representable")
      d
    })
    val terms = shifted.map(math.exp)
    val sum = terms.sum
    val logSum = math.log(sum)
    val normalized = shifted.map(l => {
      val d = l-logSum
      if (l.isFinite && !d.isFinite) throw new ArithmeticException("Normalized log weight overflow")
      d
    })
    Normalized(normalized, terms.map(_/sum), peak, logSum)
  }

  def add(a: Double, b: Double): Double = {
    if (a == Double.NegativeInfinity) b
    else if (b == Double.NegativeInfinity) a
    else math.max(a,b) + math.log1p(math.exp(math.min(a,b)-math.max(a,b)))
  }

  /** U=2^-k*(1+v), P(k=j)=2^-j, v uniform in [0,1).
    * Leading zero words extend the exponent instead of mapping U=0 to -Infinity.
    * Independent mantissa bits give relative resolution in the far tail, unlike
    * repeatedly extending only zero nextDouble values. Cancellation near U=1 is
    * harmless here: tree comparisons always use the smaller child's p <= 1/2.
    */
  def logUniform(rng: java.util.Random): Double = {
    var zeros = 0.0
    var bits = rng.nextLong()
    while (bits == 0L) {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("Ancestor sampling interrupted")
      zeros += 64.0
      bits = rng.nextLong()
    }
    val exponent = zeros + java.lang.Long.numberOfLeadingZeros(bits) + 1.0
    -exponent*math.log(2.0) + math.log1p(rng.nextDouble())
  }

  /** Single balanced tree specialization of Lee/Whiteley Algorithm 4.
    * O(N) preparation/storage, O(log N) independent decisions per draw.
    */
  final class Tree(logWeights: Vector[Double]) {
    require(logWeights.nonEmpty && logWeights.exists(_.isFinite) &&
      logWeights.forall(l => l.isFinite || l == Double.NegativeInfinity))
    private val leaves = Integer.highestOneBit(logWeights.size-1) match {
      case 0 => 1
      case n => n*2
    }
    private val mass = Array.fill(leaves*2)(Double.NegativeInfinity)
    private val small = new Array[Int](leaves)
    private val threshold = new Array[Double](leaves)
    logWeights.indices.foreach(i => mass(leaves+i) = logWeights(i))
    for (i <- (1 until leaves).reverse) {
      val left = 2*i; val right = left+1
      mass(i) = add(mass(left),mass(right))
      small(i) = if (mass(left) <= mass(right)) left else right
      val low = mass(small(i)); val high = mass(left+right-small(i))
      threshold(i) = if (low == Double.NegativeInfinity) Double.NegativeInfinity else {
        val d = low-high
        if (!d.isFinite) throw new ArithmeticException("Ancestor log odds not representable")
        d-math.log1p(math.exp(d))
      }
    }
    def draw(logUniform: => Double): Int = {
      var node = 1
      while (node < leaves) {
        val s = small(node)
        val chooseSmall = threshold(node) != Double.NegativeInfinity && logUniform < threshold(node)
        node = if (chooseSmall) s else 4*node+1-s
      }
      node-leaves
    }
  }
}
