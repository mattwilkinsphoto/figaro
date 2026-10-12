package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*

/** Actual Figaro outputs consumed by tools/capability_reference.py; no timing claims. */
object CapabilityNumericalStudy {
  def main(args: Array[String]): Unit = {
    for(shape <- Vector(-1e-100,-1e-20,1e-20,1e-100); x <- Vector(.01,.5,2.0,10.0)) {
      val gev=GeneralizedExtremeValueDistribution(shape)
      val gpd=GeneralizedParetoDistribution(shape)
      println(s"CAPABILITY,GEV,$shape,$x,${gev.logDensity(x)},${gev.cdf(x)},${gev.survival(x)}")
      println(s"CAPABILITY,GPD,$shape,$x,${gpd.logDensity(x)},${gpd.cdf(x)},${gpd.survival(x)}")
    }
    val eye=Vector(Vector(1.0,0.0),Vector(0.0,1.0))
    for(eps <- Vector(.1,1e-20,1e-100)) {
      val lower=Vector(Vector(1.0,0.0),Vector(1.0,eps))
      println(s"CAPABILITY,WISHART,4.0,$eps,${WishartDistribution(4,eye).logDensityCholesky(lower)}")
      println(s"CAPABILITY,INVERSE_WISHART,4.0,$eps,${InverseWishartDistribution(4,eye).logDensityCholesky(lower)}")
    }
  }
}
