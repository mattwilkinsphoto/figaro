package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.{MultiChainVectorSliceSampler as MC, McmcDiagnostics}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.jdk.CollectionConverters.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MultiChainVectorSliceSamplerRegressionTest extends AnyWordSpec with Matchers {
  private val normal: Vector[Double] => Double = x => -x.map(v => v * v).sum / 2
  private def cfg(m: VS.Method = VS.Method.GPSS) = MC.Config(VS.Config(m, draws = 400, warmUp = 50, seed = 729))
  private def model(i: Int, seed: Long): MC.Model = MC.Model(Vector(i + 0.5, -i - 0.5), normal)

  "Multi-chain vector sampling" should {
    "partition benchmark time without changing public output or seeded work" in {
      val c = cfg()
      val (measured, times) = MC.measuredRun(c)(model)
      val ordinary = MC.run(c)(model)
      measured.chains shouldBe ordinary.chains
      measured.diagnostics shouldBe ordinary.diagnostics
      measured.warnings shouldBe ordinary.warnings
      val phases = Vector(times.constructionSeconds, times.samplingAndShutdownSeconds, times.diagnosticsSeconds)
      phases.foreach(t => { t.isFinite shouldBe true; t should be >= 0.0 })
      phases.sum shouldBe measured.elapsedSeconds +- 1e-12
    }

    "match individual kernels exactly across worker counts and assign seeds in index order" in {
      for (method <- VS.Method.values) {
        val c = cfg(method)
        val sequential = MC.run(c.copy(parallelism = 1))(model)
        val parallel = MC.run(c.copy(parallelism = 2))(model)
        val oversubscribed = MC.run(c.copy(parallelism = 9))(model)
        sequential.chains shouldBe parallel.chains
        sequential.chains shouldBe oversubscribed.chains
        sequential.diagnostics shouldBe parallel.diagnostics
        val seeds = new java.util.SplittableRandom(c.sampler.seed)
        sequential.chains.zipWithIndex.foreach { (chain, i) =>
          chain.index shouldBe i
          chain.seed shouldBe seeds.nextLong()
          val m = model(i, chain.seed)
          chain.result shouldBe VS.run(c.sampler.copy(seed = chain.seed), m.initial)(m.logDensity)
        }
        sequential.diagnosticDrawsPerChain shouldBe 400
        sequential.warnings shouldBe empty
        sequential.diagnostics shouldBe Vector.tabulate(2)(j =>
          McmcDiagnostics.summarize(sequential.chains.map(_.result.samples.map(_(j)))))
      }
    }

    "construct models serially and bound workers while retaining all chains" in {
      val caller = Thread.currentThread()
      val active = new AtomicInteger()
      val maximum = new AtomicInteger()
      val entered = new CountDownLatch(2)
      val workerThreads = new ConcurrentLinkedQueue[Thread]()
      var built = Vector.empty[Int]
      val result = MC.run(cfg().copy(chains = 5, parallelism = 2)) { (i, seed) =>
        Thread.currentThread() shouldBe caller
        built :+= i
        var first = true
        MC.Model(model(i, seed).initial, x => {
          built shouldBe Vector(0, 1, 2, 3, 4)
          if (first) {
            first = false
            workerThreads.add(Thread.currentThread())
            val n = active.incrementAndGet()
            maximum.accumulateAndGet(n, (a, b) => math.max(a, b))
            try { entered.countDown(); require(entered.await(5, TimeUnit.SECONDS)) }
            finally active.decrementAndGet()
          }
          normal(x)
        })
      }
      maximum.get() shouldBe 2
      result.chains.size shouldBe 5
      workerThreads.asScala.toVector.distinct.size shouldBe 2
      workerThreads.asScala.foreach(_.isAlive shouldBe false)
    }

    "validate aggregate limits and all models before any density evaluation" in {
      intercept[IllegalArgumentException](MC.Config(null))
      intercept[IllegalArgumentException](cfg().copy(chains = 1))
      intercept[IllegalArgumentException](cfg().copy(parallelism = 0))
      intercept[IllegalArgumentException](cfg().copy(maxStoredValues = 1))
      intercept[IllegalArgumentException](cfg().copy(shutdownTimeoutMillis = 0))
      intercept[IllegalArgumentException](cfg().copy(shutdownTimeoutMillis = 30001))
      intercept[IllegalArgumentException](MC.run(null)(model))
      intercept[IllegalArgumentException](MC.run(cfg())(null))
      val calls = new AtomicInteger()
      val f: Vector[Double] => Double = x => { calls.incrementAndGet(); normal(x) }
      val badModels = Vector(null, MC.Model(null, f), MC.Model(Vector(1.0, 1.0), null),
        MC.Model(Vector(Double.NaN, 1.0), f), MC.Model(Vector(0.0, 0.0), f),
        MC.Model(Vector(1.0), f), MC.Model(Vector(1.0, 1.0, 1.0), f))
      badModels.foreach { bad =>
        val error = intercept[MC.ChainFailure] {
          MC.run(cfg()) { (i, _) => if (i == 1) bad else MC.Model(Vector(1.0, 1.0), f) }
        }
        error.chainIndex shouldBe 1
      }
      intercept[MC.ChainFailure](MC.run(cfg().copy(maxStoredValues = 1600))((_, _) => MC.Model(Vector(1.0, 1.0), f)))
      intercept[MC.ChainFailure](MC.run(cfg().copy(sampler = cfg().sampler.copy(maxStoredValues = 400)))((_, _) => MC.Model(Vector(1.0, 1.0), f)))
      calls.get() shouldBe 0
    }

    "retain capped chains and explicitly align diagnostics without discarding excess draws" in {
      val c = cfg(VS.Method.Quantile).copy(sampler = VS.Config(VS.Method.Quantile, draws = 1000,
        warmUp = 10, seed = 178, maxEvaluations = 400))
      val result = MC.run(c)(model)
      result.chains.forall(_.result.reason == VS.StopReason.MaxEvaluationsReached) shouldBe true
      result.chains.map(_.result.samples.size).distinct.size should be > 1
      val n = result.chains.map(_.result.samples.size).min
      result.diagnosticDrawsPerChain shouldBe n
      result.warnings.size shouldBe 2
      for (chain <- result.chains) {
        chain.result.evaluations shouldBe 400L
        val m = model(chain.index, chain.seed)
        chain.result shouldBe VS.run(c.sampler.copy(seed = chain.seed), m.initial)(m.logDensity)
      }
      result.diagnostics shouldBe Vector.tabulate(2)(j =>
        McmcDiagnostics.summarize(result.chains.map(_.result.samples.take(n).map(_(j)))))
      val tiny = MC.run(c.copy(sampler = c.sampler.copy(maxEvaluations = 1)))(model)
      tiny.chains.size shouldBe 4
      tiny.chains.forall(_.result.samples.isEmpty) shouldBe true
      tiny.diagnostics shouldBe empty
      tiny.diagnosticDrawsPerChain shouldBe 0
      tiny.warnings.size shouldBe 2
    }

    "detect a later chain failure promptly and join cooperatively cancelled siblings" in {
      val entered = new CountDownLatch(1)
      val block = new CountDownLatch(1)
      val workers = new ConcurrentLinkedQueue[Thread]()
      val sentinel = new IllegalStateException("chain one failed")
      val error = intercept[MC.ChainFailure] {
        MC.run(cfg().copy(chains = 2, parallelism = 2)) { (i, _) =>
          MC.Model(Vector(1.0, 1.0), x => {
            workers.add(Thread.currentThread())
            if (i == 0) { entered.countDown(); block.await(); normal(x) }
            else { require(entered.await(5, TimeUnit.SECONDS)); throw sentinel }
          })
        }
      }
      error.chainIndex shouldBe 1
      error.getCause should be theSameInstanceAs sentinel
      workers.asScala.foreach(_.isAlive shouldBe false)
    }

    "preserve factory exceptions and factory or caller interruption" in {
      val sentinel = new IllegalArgumentException("factory failure")
      val error = intercept[MC.ChainFailure](MC.run(cfg())((_, _) => throw sentinel))
      error.chainIndex shouldBe 0
      error.getCause should be theSameInstanceAs sentinel
      try {
        val stop = new InterruptedException("factory interruption")
        intercept[InterruptedException](MC.run(cfg())((_, _) => throw stop)) should be theSameInstanceAs stop
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
      try {
        Thread.currentThread().interrupt()
        intercept[InterruptedException](MC.run(cfg())((_, _) => fail("Factory must not run")))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }

    "cancel a running pool on caller interruption and restore its flag after joining" in {
      val entered = new CountDownLatch(2)
      val block = new CountDownLatch(1)
      val workers = new ConcurrentLinkedQueue[Thread]()
      val error = new AtomicReference[Throwable]()
      val flag = new AtomicReference[Boolean](false)
      val caller = new Thread(new Runnable {
        def run(): Unit = try {
          MC.run(cfg().copy(parallelism = 2)) { (_, _) =>
            MC.Model(Vector(1.0, 1.0), x => {
              workers.add(Thread.currentThread()); entered.countDown(); block.await(); normal(x)
            })
          }
        } catch { case e: Throwable => error.set(e); flag.set(Thread.currentThread().isInterrupted) }
      })
      caller.setDaemon(true); caller.start()
      try {
        entered.await(5, TimeUnit.SECONDS) shouldBe true
        caller.interrupt(); caller.join(5000)
        caller.isAlive shouldBe false
        error.get() shouldBe a[InterruptedException]
        flag.get() shouldBe true
        workers.asScala.foreach(_.isAlive shouldBe false)
      } finally { block.countDown(); caller.interrupt(); caller.join(5000) }
    }

    "report an uncooperative callback as a bounded shutdown failure without hiding the primary error" in {
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val worker = new AtomicReference[Thread]()
      val sentinel = new IllegalStateException("primary")
      try {
        val error = intercept[MC.ChainFailure] {
          MC.run(cfg().copy(chains = 2, shutdownTimeoutMillis = 100)) { (i, _) =>
            MC.Model(Vector(1.0, 1.0), x => {
              if (i == 0) {
                worker.set(Thread.currentThread()); entered.countDown()
                var done = false
                while (!done) {
                  try { release.await(); done = true } catch { case _: InterruptedException => () }
                }
                // Model cooperates again after explicit test release.
                throw new InterruptedException("released")
              } else { require(entered.await(5, TimeUnit.SECONDS)); throw sentinel }
            })
          }
        }
        error.getCause should be theSameInstanceAs sentinel
        error.getSuppressed.exists(_.isInstanceOf[IllegalStateException]) shouldBe true
        worker.get().isAlive shouldBe true
      } finally {
        release.countDown()
        if (worker.get() != null) { worker.get().join(5000); worker.get().isAlive shouldBe false }
      }
    }

    "finish shutdown despite repeated caller interrupts without touching callback-owned resources" in {
      val entered = new CountDownLatch(1)
      val interruptedWorker = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val workers = new ConcurrentLinkedQueue[Thread]()
      val error = new AtomicReference[Throwable]()
      val flag = new AtomicReference[Boolean](false)
      val caller = new Thread(new Runnable {
        def run(): Unit = try {
          MC.run(cfg().copy(chains = 2, parallelism = 1)) { (_, _) =>
            MC.Model(Vector(1.0, 1.0), _ => {
              workers.add(Thread.currentThread()); entered.countDown()
              var done = false
              while (!done) {
                try { release.await(); done = true }
                catch { case _: InterruptedException => interruptedWorker.countDown() }
              }
              throw new InterruptedException("caller releases resource")
            })
          }
        } catch { case e: Throwable => error.set(e); flag.set(Thread.currentThread().isInterrupted) }
      })
      caller.setDaemon(true); caller.start()
      try {
        entered.await(5, TimeUnit.SECONDS) shouldBe true
        caller.interrupt()
        interruptedWorker.await(5, TimeUnit.SECONDS) shouldBe true
        caller.interrupt()
        release.countDown()
        caller.join(5000)
        caller.isAlive shouldBe false
        error.get() shouldBe a[InterruptedException]
        flag.get() shouldBe true
        workers.asScala.foreach(_.isAlive shouldBe false)
      } finally { release.countDown(); caller.interrupt(); caller.join(5000) }
    }

    "isolate nested runs and aggregate diagnostics without changing outer streams" in {
      val c = cfg().copy(chains = 2, parallelism = 1)
      val expected = MC.run(c)(model)
      val nested = MC.run(c) { (i, seed) =>
        var first = true
        MC.Model(model(i, seed).initial, x => {
          if (first) {
            first = false
            MC.run(c.copy(sampler = c.sampler.copy(draws = 4, warmUp = 0)))(model).chains.size shouldBe 2
          }
          normal(x)
        })
      }
      nested.chains shouldBe expected.chains
      nested.diagnostics shouldBe expected.diagnostics
      nested.diagnostics.foreach { summary =>
        summary.mean shouldBe 0.0 +- 0.25
        summary.rHat.get should be < 1.1
      }
    }

    "propagate kernel search failures instead of fabricating constant-chain success" in {
      // A deliberately nonintegrable measure-zero target is a failure, not valid chain output.
      val error = intercept[MC.ChainFailure] {
        MC.run(cfg(VS.Method.Quantile).copy(sampler = VS.Config(VS.Method.Quantile, maxSearch = 1))) { (_, _) =>
          MC.Model(Vector(1.0), x => if (x.head == 1) 0 else Double.NegativeInfinity)
        }
      }
      error.getCause shouldBe a[VS.SearchExhausted]
    }
  }
}
