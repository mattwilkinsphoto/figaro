package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.ProposalScheme
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings.*
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings.{run as runMcmc}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal
import com.cra.figaro.util.random
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters.*

class MultiChainMcmcRegressionTest extends AnyWordSpec with Matchers {
  private val small = Config(chains = 2, drawsPerChain = 20, warmUp = 10, parallelism = 2)
  private def coin(u: Universe, index: Int): Model = {
    val x = Flip(0.3)(using "", u)
    Model(Vector(Observable("x", x)(b => if (b) 1.0 else 0.0)))
  }
  private def workers = Thread.getAllStackTraces.keySet().asScala.filter(_.getName.startsWith("figaro-mcmc-worker-")).toSet

  "Multi-chain MH" should {
    "match the ordinary MH retained states with the same initialization, warm-up, and thinning" in {
      val config = small.copy(drawsPerChain = 500, warmUp = 21, thin = 3)
      val result = runMcmc(config)(coin)
      result.chains.foreach { chain =>
        val expected = com.cra.figaro.util.withRandomSeed(chain.seed) {
          val u = new Universe
          val x = Flip(0.3)(using "", u)
          val trace = Vector.newBuilder[Double]
          val sampler = new com.cra.figaro.algorithm.sampling.OneTimeMetropolisHastings(
            u, config.drawsPerChain, ProposalScheme.default(using u), config.warmUp, config.thin, x) {
            override def sample(): (Boolean, Sample) = {
              val sampled = super.sample()
              if (sampled._1) trace += (if (x.value) 1.0 else 0.0)
              sampled
            }
          }
          try { sampler.start(); trace.result() }
          finally { if (sampler.isActive) sampler.kill(); u.clear() }
        }
        chain.draws("x") shouldBe expected
      }
    }
    "return exact per-chain budgets, independent streams, and identical traces across worker counts" in {
      val config = small.copy(chains = 4, drawsPerChain = 1000, thin = 3)
      val serial = runMcmc(config.copy(parallelism = 1))(coin)
      val parallel = runMcmc(config.copy(parallelism = 4))(coin)
      serial.chains.map(_.draws) shouldBe parallel.chains.map(_.draws)
      serial.diagnostics shouldBe parallel.diagnostics
      serial.chains.map(_.seed).distinct.size shouldBe 4
      serial.chains.map(_.draws).distinct.size shouldBe 4
      serial.chains.map(_.index) shouldBe Vector(0, 1, 2, 3)
      serial.chains.foreach { c =>
        c.draws("x").size shouldBe 1000
        c.acceptanceRate should (be >= 0.0 and be <= 1.0)
      }
      workers shouldBe empty
    }
    "recover a known discrete posterior with aligned projections" in {
      val result = runMcmc(Config(drawsPerChain = 10000)) { (u, _) =>
        val x = Flip(0.3)(using "", u)
        x.addConstraint((b: Boolean) => if (b) 0.8 else 0.2)
        Model(Vector(Observable("x", x)(b => if (b) 1.0 else 0.0),
          Observable("notX", x)(b => if (b) 0.0 else 1.0)))
      }
      result.diagnostics("x").mean shouldBe (0.24 / 0.38 +- 0.02)
      result.diagnostics("x").rHat.get should be < 1.02
      result.chains.foreach(c => c.draws("x").zip(c.draws("notX")).foreach((x, y) => x + y shouldBe 1.0))
    }
    "recover a conjugate continuous posterior using an explicit log likelihood" in {
      val result = runMcmc(Config(drawsPerChain = 12000, warmUp = 2000)) { (u, _) =>
        val x = Normal(0.0, 1.0)(using "", u)
        // y=1 with unit noise: posterior N(0.5, variance 0.5).
        x.addLogConstraint((v: Double) => -0.5 * (v - 1) * (v - 1))
        Model(Vector(Observable("x", x)(identity)))
      }
      result.diagnostics("x").mean shouldBe (0.5 +- 0.06)
      result.diagnostics("x").standardDeviation shouldBe (math.sqrt(0.5) +- 0.06)
      result.diagnostics("x").rHat.get should be < 1.03
      result.chains.foreach(c => c.draws("x").sliding(2).exists(v => v(0) == v(1)) shouldBe true)
    }
    "retain rejected states instead of reporting the biased accepted-move distribution" in {
      val result = runMcmc(Config(drawsPerChain = 10000)) { (u, _) =>
        val x = Normal(0.0, 1.0)(using "", u)
        x.addCondition((v: Double) => v > 0)
        Model(Vector(Observable("x", x)(identity)))
      }
      result.diagnostics("x").mean shouldBe (math.sqrt(2 / math.Pi) +- 0.06)
      result.chains.foreach { c =>
        all(c.draws("x")) should be > 0.0
        c.draws("x").sliding(2).count(v => v(0) == v(1)) should be > 100
        c.acceptanceRate should be < 1.0
      }
    }
    "keep dynamic elements in the supplied universe" in {
      val wrong = new ConcurrentLinkedQueue[Universe]
      val result = runMcmc(Config(drawsPerChain = 6000)) { (u, _) =>
        val x = Chain(Flip(0.4), (b: Boolean) => {
          val child = Flip(if (b) 0.9 else 0.1)
          if (child.universe ne u) wrong.add(child.universe)
          child
        })
        Model(Vector(Observable("x", x)(b => if (b) 1.0 else 0.0)))
      }
      wrong shouldBe empty
      result.diagnostics("x").mean shouldBe (0.42 +- 0.035)
    }
    "diagnose chains trapped in separate modes despite acceptable within-chain movement" in {
      val result = runMcmc(small.copy(drawsPerChain = 3000, maxInitializationAttempts = 10000)) { (u, index) =>
        val mode = Flip(0.5)(using "", u)
        val noise = Normal(0.0, 1.0)(using "", u)
        val x = Apply(mode, noise, (b: Boolean, n: Double) => n + (if (b) 10 else -10))(using "", u)
        Model(Vector(Observable("x", x)(identity)), Some(ProposalScheme(noise)),
          () => mode.value == (index == 0))
      }
      result.diagnostics("x").rHat.get should be > 1.1
      result.diagnostics("x").warnings should not be empty
    }
    "clean models and preserve caller universe and RNG on success" in {
      val original = Universe.createNew()
      val models = new ConcurrentLinkedQueue[Universe]
      random.setSeed(726L)
      runMcmc(small) { (u, i) => models.add(u); coin(u, i) }
      models.asScala.foreach(_.activeElements shouldBe empty)
      Universe.universe should be theSameInstanceAs original
      random.nextDouble() shouldBe new scala.util.Random(726L).nextDouble()
      workers shouldBe empty
    }
    "clean every constructed model when a later factory fails" in {
      val models = new ConcurrentLinkedQueue[Universe]
      val error = intercept[ChainFailure] {
        runMcmc(small.copy(chains = 4)) { (u, i) =>
          models.add(u)
          val m = coin(u, i)
          if (i == 2) throw new IllegalStateException("factory failed")
          m
        }
      }
      error.chainIndex shouldBe 2
      error.getCause.getMessage shouldBe "factory failed"
      models.asScala.foreach(_.activeElements shouldBe empty)
      workers shouldBe empty
    }
    "fail fast and clean both queued and running chains after a projection failure" in {
      val models = new ConcurrentLinkedQueue[Universe]
      val error = intercept[ChainFailure] {
        runMcmc(small.copy(chains = 8, drawsPerChain = 10000)) { (u, i) =>
          models.add(u)
          val x = Flip(0.5)(using "", u)
          Model(Vector(Observable("x", x) { b =>
            if (i == 0) throw new IllegalStateException("projection failed")
            if (b) 1.0 else 0.0
          }))
        }
      }
      error.chainIndex shouldBe 0
      models.asScala.foreach(_.activeElements shouldBe empty)
      workers shouldBe empty
    }
    "cancel cooperatively, restore caller interruption, and join workers before returning" in {
      val entered = new CountDownLatch(1)
      val result = new AtomicReference[Throwable]
      val flag = new AtomicBoolean
      val models = new ConcurrentLinkedQueue[Universe]
      val caller = new Thread(() => {
        try runMcmc(small.copy(drawsPerChain = 100000)) { (u, _) =>
          models.add(u)
          val x = Flip(0.5)(using "", u)
          Model(Vector(Observable("x", x) { b =>
            entered.countDown()
            Thread.sleep(10000)
            if (b) 1.0 else 0.0
          }))
        } catch { case e: Throwable => result.set(e); flag.set(Thread.currentThread().isInterrupted) }
        ()
      })
      caller.setDaemon(true)
      caller.start()
      try {
        entered.await(10, TimeUnit.SECONDS) shouldBe true
        caller.interrupt()
        caller.join(10000)
        caller.isAlive shouldBe false
        result.get() shouldBe a[InterruptedException]
        flag.get() shouldBe true
        models.asScala.foreach(_.activeElements shouldBe empty)
        workers shouldBe empty
      } finally { caller.interrupt(); caller.join(10000) }
    }
    "preserve an interrupt thrown during model construction" in {
      try {
        intercept[InterruptedException] { runMcmc(small) { (_, _) => throw new InterruptedException("factory") } }
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }
    "dispose queued models without clearing a still-running uncooperative worker after interrupted shutdown" in {
      val entered = new CountDownLatch(1)
      val noticedCancellation = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val failure = new AtomicReference[Throwable]
      val models = new ConcurrentLinkedQueue[Universe]
      val caller = new Thread(() => {
        try runMcmc(small.copy(chains = 4, parallelism = 1)) { (u, _) =>
          models.add(u)
          val x = Flip(0.5)(using "", u)
          Model(Vector(Observable("x", x) { b =>
            entered.countDown()
            var waiting = true
            while (waiting) {
              try { release.await(); waiting = false }
              catch { case _: InterruptedException => noticedCancellation.countDown() }
            }
            if (b) 1.0 else 0.0
          }))
        } catch { case e: Throwable => failure.set(e) }
        ()
      })
      caller.setDaemon(true)
      caller.start()
      try {
        entered.await(10, TimeUnit.SECONDS) shouldBe true
        caller.interrupt()
        noticedCancellation.await(10, TimeUnit.SECONDS) shouldBe true
        // A second caller interrupt aborts awaitTermination while a worker still
        // deliberately ignores cancellation. No wall-clock sleep or 30-second test.
        caller.interrupt()
        caller.join(10000)
        caller.isAlive shouldBe false
        failure.get() shouldBe a[InterruptedException]
        failure.get().getSuppressed.toVector.exists(_.isInstanceOf[InterruptedException]) shouldBe true
        val universes = models.asScala.toVector
        universes.head.activeElements should not be empty
        universes.tail.foreach(_.activeElements shouldBe empty)
      } finally {
        release.countDown()
        caller.interrupt()
        caller.join(10000)
        workers.foreach(_.join(10000))
      }
      workers shouldBe empty
      models.asScala.foreach(_.activeElements shouldBe empty)
    }
    "bound initialization attempts for impossible evidence" in {
      val error = intercept[ChainFailure] {
        runMcmc(small.copy(maxInitializationAttempts = 3)) { (u, _) =>
          val x = Flip(0.3)(using "", u)
          x.addCondition((_: Boolean) => false)
          Model(Vector(Observable("x", x)(b => if (b) 1.0 else 0.0)))
        }
      }
      error.getCause.getMessage should include ("initial state")
      workers shouldBe empty
    }
    "reject invalid budgets, schema mismatches, observations, and non-finite projections" in {
      intercept[IllegalArgumentException](Config(chains = 1))
      intercept[IllegalArgumentException](Config(drawsPerChain = 3))
      intercept[IllegalArgumentException](Config(warmUp = -1))
      intercept[IllegalArgumentException](Config(parallelism = 0))
      intercept[IllegalArgumentException](Config(thin = 0))
      intercept[IllegalArgumentException](Config(drawsPerChain = Int.MaxValue, thin = 2))
      intercept[IllegalArgumentException](Config(maxStoredValues = 10))
      intercept[ChainFailure] { runMcmc(small) { (u, i) => Model(Vector(Observable(i.toString, Flip(0.3)(using "", u))(_ => 1.0))) } }
      intercept[ChainFailure] { runMcmc(small) { (u, _) =>
        val x = Flip(0.3)(using "", u); x.observe(true)
        Model(Vector(Observable("x", x)(_ => 1.0)))
      } }
      intercept[ChainFailure] { runMcmc(small) { (u, _) => Model(Vector(Observable("x", Flip(0.3)(using "", u))(_ => Double.NaN))) } }
      intercept[ChainFailure] { runMcmc(small) { (u, _) =>
        val x = Flip(0.3)(using "", u)
        val query = Observable("x", x)(_ => 1.0)
        x.deactivate()
        Model(Vector(query))
      } }
      intercept[ChainFailure] { runMcmc(small.copy(maxStoredValues = 40)) { (u, _) =>
        val x = Flip(0.3)(using "", u)
        Model(Vector(Observable("x", x)(_ => 1.0), Observable("y", x)(_ => 1.0)))
      } }
    }
    "reject foreign observable and custom proposal targets without clearing the foreign universe" in {
      val foreign = new Universe
      val x = Flip(0.5)(using "", foreign)
      try {
        intercept[ChainFailure] { runMcmc(small) { (_, _) => Model(Vector(Observable("x", x)(_ => 1.0))) } }
        intercept[ChainFailure] { runMcmc(small) { (u, i) => coin(u, i).copy(proposal = Some(ProposalScheme(x))) } }
        foreign.activeElements should contain (x)
      } finally foreign.clear()
    }
    "reject observed dynamic children and foreign dynamic dependencies" in {
      intercept[ChainFailure] {
        runMcmc(small) { (_, _) =>
          val x = Chain(Flip(0.5), (_: Boolean) => { val child = Flip(0.5); child.observe(true); child })
          Model(Vector(Observable("x", x)(_ => 1.0)))
        }
      }
      val foreign = new Universe
      val child = Flip(0.5)(using "", foreign)
      try {
        intercept[ChainFailure] {
          runMcmc(small) { (_, _) =>
            val x = Chain(Flip(0.5), (_: Boolean) => child)
            Model(Vector(Observable("x", x)(_ => 1.0)))
          }
        }
        foreign.activeElements should contain (child)
      } finally foreign.clear()
      workers shouldBe empty
    }
    "reject NaN likelihoods encountered after valid initialization" in {
      val error = intercept[ChainFailure] {
        runMcmc(small.copy(drawsPerChain = 1000)) { (u, _) =>
          val x = Flip(0.5)(using "", u)
          var initialized = false
          // Deliberately invalid callback tests the fail-closed boundary.
          x.addLogConstraint((_: Boolean) => if (initialized) Double.NaN else 0.0)
          Model(Vector(Observable("x", x)(_ => 1.0)), initialState = () => { initialized = true; true })
        }
      }
      error.getCause.getMessage should include ("likelihood")
      workers shouldBe empty
    }
  }
}
