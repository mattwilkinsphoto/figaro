package com.cra.figaro.library.atomic.discrete

private[discrete] object LegacyCountRandom {
  def adapter(): org.apache.commons.math3.random.RandomGenerator = new org.apache.commons.math3.random.AbstractRandomGenerator {
    private var calls=0
    def setSeed(seed: Long): Unit=throw new UnsupportedOperationException("caller owns RNG")
    def nextDouble(): Double={
      calls+=1
      if(calls>100000) throw new ArithmeticException("count sampler RNG budget exceeded")
      com.cra.figaro.library.atomic.DistributionNumerics.open(com.cra.figaro.util.random)
    }
  }
}
