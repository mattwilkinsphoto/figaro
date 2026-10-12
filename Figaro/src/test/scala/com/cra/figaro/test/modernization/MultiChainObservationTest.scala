package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings.{run as runMcmc, *}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MultiChainObservationTest extends AnyWordSpec with Matchers {
  private val config = Config(chains = 2, drawsPerChain = 8000, warmUp = 1000, parallelism = 2, seed = 831L)

  "Owned multi-chain observations" should {
    "recover a Bernoulli parameter posterior without rewriting the observation" in {
      val result = runMcmc(config) { (u, _) =>
        val p = Select(0.5 -> 0.2, 0.5 -> 0.8)(using "p", u)
        Flip(p)(using "y", u).observe(true)
        Model(Vector(Observable("high", p)(p => if (p == 0.8) 1.0 else 0.0)))
      }
      result.diagnostics("high").mean shouldBe (0.8 +- 0.025)
    }

    "include the normalizing term when a conditional scale is inferred" in {
      val result = runMcmc(config) { (u, _) =>
        val scale = Select(0.5 -> 0.5, 0.5 -> 2.0)(using "scale", u)
        val kernel = Apply(scale, (s: Double) => LaplaceDistribution(0.0, s))(using "", u)
        ScalarElement(kernel)(using "y", u).observe(0.0)
        Model(Vector(Observable("small", scale)(s => if (s == 0.5) 1.0 else 0.0)))
      }
      // Densities at zero are 1 and 1/4, not equal unnormalized exponentials.
      result.diagnostics("small").mean shouldBe (0.8 +- 0.025)
    }

    "recover a conjugate Normal posterior through an observed dynamic likelihood" in {
      val result = runMcmc(config) { (u, _) =>
        val mean = Normal(0.0, 1.0)(using "mean", u)
        Normal(mean, 1.0)(using "y", u).observe(1.0)
        Model(Vector(Observable("mean", mean)(identity), Observable("square", mean)(x => x * x)))
      }
      result.diagnostics("mean").mean shouldBe (0.5 +- 0.04)
      result.diagnostics("square").mean shouldBe (0.75 +- 0.06)
    }

    "preserve per-chain replay as the worker count changes" in {
      def build(u: Universe, index: Int): Model = {
        val scale = Select(0.5 -> 0.5, 0.5 -> 2.0)(using "scale", u)
        val kernel = Apply(scale, (s: Double) => LaplaceDistribution(0.0, s))(using "", u)
        ScalarElement(kernel)(using "y", u).observe(0.0)
        Model(Vector(Observable("scale", scale)(identity)))
      }
      val one = runMcmc(config.copy(parallelism = 1, drawsPerChain = 1000))(build)
      val two = runMcmc(config.copy(parallelism = 2, drawsPerChain = 1000))(build)
      one.chains.map(_.draws) shouldBe two.chains.map(_.draws)
    }

    "reject zero-likelihood proposals without accepting impossible states" in {
      val result = runMcmc(config) { (u, _) =>
        val p = Select(0.5 -> 0.0, 0.5 -> 0.1)(using "p", u)
        Flip(p)(using "y", u).observe(true)
        Model(Vector(Observable("p", p)(identity)))
      }
      result.chains.foreach(_.draws("p").distinct shouldBe Vector(0.1))
    }

    "refuse a shared conditional likelihood child" in {
      intercept[ChainFailure] {
        runMcmc(config.copy(drawsPerChain = 10)) { (u, _) =>
          val latent = Flip(0.5)(using "latent", u)
          val shared = Laplace(0.0, 1.0)(using "shared", u)
          Chain(latent, (_: Boolean) => shared)(using "y", u).observe(0.0)
          Model(Vector(Observable("latent", latent)(b => if (b) 1.0 else 0.0)))
        }
      }
    }

    "refuse extra latent nodes inside an observed likelihood callback" in {
      intercept[ChainFailure] {
        runMcmc(config.copy(drawsPerChain=10)) { (u,_) =>
          val x=Flip(.5)(using "x",u)
          Chain(x, (_: Boolean) => {
            Flip(.5)(using "extra",u)
            Laplace(0,1)(using "",u)
          })(using "y",u).observe(0.0)
          Model(Vector(Observable("x",x)(b => if(b) 1.0 else 0.0)))
        }
      }
    }

    "refuse changing observation values during initialization" in {
      intercept[ChainFailure] {
        runMcmc(config.copy(drawsPerChain=10)) { (u,_) =>
          val x=Normal(0,1)(using "x",u)
          val y=Normal(x,1)(using "y",u); y.observe(1)
          Model(Vector(Observable("x",x)(identity)),initialState=() => { y.observe(2); true })
        }
      }
    }

    "fail bounded initialization for genuinely impossible observations" in {
      intercept[ChainFailure] {
        runMcmc(config.copy(drawsPerChain=10,maxInitializationAttempts=3)) { (u,_) =>
          val x=Flip(.5)(using "x",u)
          Flip(0.0)(using "y",u).observe(true)
          Model(Vector(Observable("x",x)(b => if(b) 1.0 else 0.0)))
        }
      }
    }

    "refuse arbitrary deterministic equality observations" in {
      intercept[ChainFailure] {
        runMcmc(config.copy(drawsPerChain = 10)) { (u, _) =>
          val x = Normal(0.0, 1.0)(using "x", u)
          Apply(x, (v: Double) => v * v)(using "square", u).observe(1.0)
          Model(Vector(Observable("x", x)(identity)))
        }
      }
    }
  }
}
