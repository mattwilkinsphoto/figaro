package com.cra.figaro.test.modernization

import com.cra.figaro.util.{SamplingRandom as SR, withRandomSeed}
import com.cra.figaro.language.Universe
import com.cra.figaro.library.atomic.continuous.Normal
import com.cra.figaro.algorithm.sampling.Importance

/** Warmed, rotated application benchmark; reported timing is never a CI pass criterion. */
object RngBenchmark {
  val algorithms = Vector(SR.Algorithm.Lxm, SR.Algorithm.Xoshiro256PlusPlus,
    SR.Algorithm.PcgRxsMXs64, SR.Algorithm.MersenneTwister, SR.Algorithm.LegacyJava)
  val budgets = Vector(2000, 8000, 32000)
  val targetMean = 30.0/21
  val targetSd = math.sqrt(1.0/21)
  @volatile private var sink = 0.0

  private def primitives(algorithm: SR.Algorithm, seed: Long, gaussian: Boolean, n: Int): Double = {
    val rng = SR.seeded(seed, algorithm)
    var sum = 0.0
    val start = System.nanoTime()
    var i = 0
    while (i < n) {
      sum += (if (gaussian) rng.nextGaussian() else rng.nextDouble())
      i += 1
    }
    val elapsed = (System.nanoTime()-start)/1e9
    sink = sum
    require(sum.isFinite)
    elapsed
  }

  def inference(algorithm: SR.Algorithm, seed: Long, n: Int): StatisticalValidationStudy.Summary =
    withRandomSeed(seed, algorithm) {
      val u = Universe.createNew()
      val mu = Normal(0, 1)(using "mu", u)
      for (i <- 0 until 20) Normal(mu, 1.0)(using "", u).observe(if (i % 2 == 0) 1.1 else 1.9)
      val alg = Importance(n, mu)(using u)
      try {
        alg.start()
        val accumulator = new StatisticalValidationStudy.Accumulator(1)
        alg.distribution(mu).foreach { case (weight, value) =>
          accumulator.add(math.log(weight), Vector(value))
        }
        val result = accumulator.result
        require(math.abs(result.mean.head-alg.mean(mu)) < 1e-10)
        result
      } finally { if (alg.isActive) alg.kill(); u.clear() }
    }

  def main(args: Array[String]): Unit = {
    require(args.length <= 1 && (args.isEmpty || Set("smoke", "philox").contains(args(0))), "Optional argument: smoke or philox")
    val smoke = args.headOption.contains("smoke")
    val selectedAlgorithms = if (args.headOption.contains("philox"))
      Vector(SR.Algorithm.Lxm, SR.Algorithm.Philox4x64) else algorithms
    val repetitions = if (smoke) 1 else 10
    val primitiveCount = if (smoke) 10000 else 1000000
    val selectedBudgets = if (smoke) Vector(2000) else budgets
    println(s"RNG_ENV,java=${System.getProperty("java.runtime.version")},os=${System.getProperty("os.name")},arch=${System.getProperty("os.arch")},processors=${Runtime.getRuntime.availableProcessors()}")
    selectedAlgorithms.foreach(a => println(s"RNG_PROVIDER,${SR.provenance(a)}"))
    // Three complete discarded passes precede all measured rows.
    for (_ <- 0 until (if (smoke) 1 else 3); a <- selectedAlgorithms) {
      primitives(a, 4242, false, primitiveCount)
      primitives(a, 4242, true, primitiveCount)
      inference(a, 4242, 2000)
    }
    println("RNG,workload,algorithm,rep,seed,draws,seconds,mean,error,mcse,ess,maxWeight,accurate,covered95")
    for (rep <- 0 until repetitions) {
      val seed = 65537L + 104729L*rep
      val order = selectedAlgorithms.drop(rep % selectedAlgorithms.size) ++ selectedAlgorithms.take(rep % selectedAlgorithms.size)
      for (a <- order; gaussian <- Vector(false, true)) {
        val time = primitives(a, seed, gaussian, primitiveCount)
        val workload = if (gaussian) "gaussian" else "uniform"
        println(s"RNG,$workload,${a.id},$rep,$seed,$primitiveCount,$time,NA,NA,NA,NA,NA,NA,NA")
      }
      for (n <- selectedBudgets; a <- order) {
        val start = System.nanoTime()
        val result = inference(a, seed, n)
        val time = (System.nanoTime()-start)/1e9
        val mean = result.mean.head
        val error = mean-targetMean
        val se = result.mcse.head
        val accurate = math.abs(error) <= .1*targetSd
        val covered = math.abs(error) <= 1.959963984540054*se
        println(s"RNG,inference,${a.id},$rep,$seed,$n,$time,$mean,$error,$se,${result.ess},${result.maxWeight},$accurate,$covered")
      }
    }
  }
}
