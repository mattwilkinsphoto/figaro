package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.Algorithm
import com.cra.figaro.algorithm.sampling.parallel.ParImportance
import com.cra.figaro.language.*
import com.cra.figaro.util.{random, withRandomSeed}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters.*

class ParallelPerformanceRegressionTest extends AnyWordSpec with Matchers {
  private def draws(r: scala.util.Random): List[Any] = List(
    r.nextDouble(), r.nextGaussian(), r.nextGaussian(), r.nextInt(), r.nextInt(37),
    r.nextLong(), r.nextFloat(), r.nextBoolean(), r.nextBytes(7).toList,
    r.nextString(5), r.nextPrintableChar(), r.shuffle(List(1, 2, 3, 4)), r.self.nextDouble())

  private class TrackedUniverse extends Universe {
    val registered = new ConcurrentLinkedQueue[Algorithm]
    override def registerAlgorithm(a: Algorithm): Unit = { super.registerAlgorithm(a); registered.add(a); () }
    override def deregisterAlgorithm(a: Algorithm): Unit = { super.deregisterAlgorithm(a); registered.remove(a); () }
  }
  private def workerThreads: Set[Thread] = Thread.getAllStackTraces.keySet().asScala
    .filter(_.getName.startsWith("figaro-importance-worker-")).toSet

  "Scoped randomness" should {
    "preserve the unscoped Scala Random sequence and stable random reference" in {
      random.setSeed(912L)
      draws(random) shouldBe draws(new scala.util.Random(912L))
      val captured = random
      withRandomSeed(17L) {
        random should be theSameInstanceAs captured
        draws(captured) shouldBe draws(new scala.util.Random(17L))
      }
    }
    "restore nested scopes and the global Gaussian cache after exceptions" in {
      random.setSeed(91L)
      val expected = new scala.util.Random(91L)
      random.nextGaussian() shouldBe expected.nextGaussian()
      withRandomSeed(41L) {
        val local = new scala.util.Random(41L)
        random.nextDouble() shouldBe local.nextDouble()
        intercept[IllegalStateException] {
          withRandomSeed(12L) { random.nextGaussian(); throw new IllegalStateException("expected") }
        }
        random.nextDouble() shouldBe local.nextDouble()
      }
      random.nextGaussian() shouldBe expected.nextGaussian()
      random.nextDouble() shouldBe expected.nextDouble()
    }
    "isolate concurrent streams without advancing the global stream" in {
      random.setSeed(812L)
      val executor = Executors.newFixedThreadPool(4)
      try {
        val futures = (0 until 4).map { index => executor.submit(new java.util.concurrent.Callable[List[Any]] {
          def call(): List[Any] = withRandomSeed(index.toLong) { draws(random) }
        }) }
        futures.zipWithIndex.foreach { case (future, index) => future.get() shouldBe draws(new scala.util.Random(index.toLong)) }
      } finally { executor.shutdown(); executor.awaitTermination(5, TimeUnit.SECONDS) }
      random.nextDouble() shouldBe new scala.util.Random(812L).nextDouble()
    }
    "route explicit reseeding only to the current scope" in {
      random.setSeed(13L)
      withRandomSeed(14L) { random.setSeed(15L); draws(random) shouldBe draws(new scala.util.Random(15L)) }
      draws(random) shouldBe draws(new scala.util.Random(13L))
    }
  }

  "Parallel importance budgets and ownership" should {
    "keep dynamically generated elements in their own worker universe" in {
      val wrong = new ConcurrentLinkedQueue[Universe]
      val original = Universe.createNew()
      val algorithm = ParImportance.seeded(() => {
        val u = Universe.createNew()
        Chain(Flip(0.4), (value: Boolean) => {
          val child = Flip(if (value) 0.9 else 0.1)
          if (child.universe ne u) wrong.add(child.universe)
          child
        })(using "query", u)
        u
      }, 4, 40000, 42L, "query")
      algorithm.start()
      try {
        wrong shouldBe empty
        algorithm.probability[Boolean]("query", true) shouldBe (0.42 +- 0.015)
      } finally algorithm.kill()
      Universe.universe should be theSameInstanceAs original
    }
    "retain remainder samples in the legacy factory" in {
      val algorithm = ParImportance(() => { Universe.createNew(); Constant(true)(using "query", Universe.universe); Universe.universe }, 3, 10, "query")
      algorithm.start()
      try math.exp(algorithm.getTotalWeight) shouldBe (10.0 +- 1e-10)
      finally algorithm.kill()
    }
    "cap active workers at the sample budget in both factories" in {
      for (seeded <- List(false, true)) {
        var calls = 0
        val generator = () => { calls += 1; val u = Universe.createNew(); Constant(true)(using "query", u); u }
        val algorithm = if (seeded) ParImportance.seeded(generator, 8, 3, 42L, "query") else ParImportance(generator, 8, 3, "query")
        calls shouldBe 3
        algorithm.start()
        try { math.exp(algorithm.getTotalWeight) shouldBe (3.0 +- 1e-10); algorithm.probability[Boolean]("query", true) shouldBe 1.0 }
        finally algorithm.kill()
      }
    }
    "reject invalid budgets before invoking the model factory" in {
      var calls = 0
      val generator = () => { calls += 1; new Universe }
      for ((workers, samples) <- List((0, 10), (-1, 10), (2, 0), (2, -1))) {
        intercept[IllegalArgumentException] { ParImportance(generator, workers, samples, "query") }
        intercept[IllegalArgumentException] { ParImportance.seeded(generator, workers, samples, 42L, "query") }
      }
      intercept[IllegalArgumentException] { ParImportance(generator, 0, "query") }
      calls shouldBe 0
    }
    "reject a shared universe in the seeded factory" in {
      val shared = new TrackedUniverse
      Universe.universe = shared
      Constant(true)(using "query", shared)
      intercept[IllegalArgumentException] { ParImportance.seeded(() => shared, 2, 10, 42L, "query") }
      Universe.universe should be theSameInstanceAs shared
      shared.registered shouldBe empty
    }
    "restore the caller default and registrations after model-factory failure" in {
      val original = Universe.createNew()
      val first = new TrackedUniverse
      var calls = 0
      intercept[IllegalStateException] {
        ParImportance.seeded(() => {
          calls += 1
          Universe.createNew()
          if (calls == 2) throw new IllegalStateException("factory failed")
          Constant(true)(using "query", first)
          first
        }, 2, 10, 42L, "query")
      }
      first.registered shouldBe empty
      Universe.universe should be theSameInstanceAs original
      workerThreads shouldBe empty
    }
    "clean up child registrations in the legacy lifecycle" in {
      val generated = scala.collection.mutable.ArrayBuffer.empty[TrackedUniverse]
      val algorithm = ParImportance(() => {
        val u = new TrackedUniverse
        generated += u
        Constant(true)(using "query", u)
        u
      }, 2, 10, "query")
      algorithm.start()
      try generated.foreach(u => u.registered.asScala.foreach(_.isActive shouldBe true))
      finally algorithm.kill()
      generated.foreach(_.registered shouldBe empty)
    }
    "scope default universes in callbacks and clean up child registrations" in {
      val original = Universe.createNew()
      val generated = scala.collection.mutable.ArrayBuffer.empty[TrackedUniverse]
      val wrongDefaults = new ConcurrentLinkedQueue[Universe]
      val algorithm = ParImportance.seeded(() => {
        val u = new TrackedUniverse
        Universe.universe = u
        generated += u
        val query = Flip(0.3)(using "query", u)
        query.addConstraint { _ => if (Universe.universe ne u) wrongDefaults.add(Universe.universe); 1.0 }
        u
      }, 4, 1003, 42L, "query")
      Universe.universe should be theSameInstanceAs original
      algorithm.start()
      try {
        wrongDefaults shouldBe empty
        generated.foreach(u => u.registered.asScala.foreach(_.isActive shouldBe true))
        math.exp(algorithm.getTotalWeight) shouldBe (1003.0 +- 1e-8)
      } finally algorithm.kill()
      generated.foreach(_.registered shouldBe empty)
      workerThreads shouldBe empty
      Universe.universe should be theSameInstanceAs original
    }
    "combine uneven worker budgets by total weight rather than averaging normalized probabilities" in {
      var index = 0
      val algorithm = ParImportance.seeded(() => {
        val u = Universe.createNew()
        val first = index == 0
        index += 1
        val target = Constant(first)(using "query", u)
        target.addConstraint(_ => if (first) 0.1 else 0.9)
        u
      }, 2, 3, 42L, "query")
      algorithm.start()
      try algorithm.probability[Boolean]("query", true) shouldBe (2.0 / 11.0 +- 1e-12)
      finally algorithm.kill()
    }
    "preserve a known weighted posterior over multiple root seeds" in {
      for (seed <- 1L to 3L) {
        val algorithm = ParImportance.seeded(() => {
          val u = Universe.createNew()
          Flip(0.3)(using "query", u).addConstraint(value => if (value) 0.8 else 0.2)
          u
        }, 4, 60000, seed, "query")
        algorithm.start()
        try algorithm.probability[Boolean]("query", true) shouldBe (0.24 / 0.38 +- 0.012)
        finally algorithm.kill()
      }
    }
    "release workers and registrations when a sampling callback fails" in {
      for (failure <- List(new IllegalStateException("callback failed"), new AssertionError("callback assertion"))) {
        val generated = scala.collection.mutable.ArrayBuffer.empty[TrackedUniverse]
        val algorithm = ParImportance.seeded(() => {
          val u = new TrackedUniverse
          Universe.universe = u
          val fail = generated.isEmpty
          generated += u
          Constant(true)(using "query", u).addConstraint { _ => if (fail) throw failure; 1.0 }
          u
        }, 2, 100, 42L, "query")
        intercept[Throwable] { algorithm.start() } should be theSameInstanceAs failure
        algorithm.isActive shouldBe false
        generated.foreach(_.registered shouldBe empty)
        workerThreads shouldBe empty
      }
    }
    "cancel cooperatively when its calling thread is interrupted, including endless rejection" in {
      for (rejectForever <- List(false, true)) {
        val entered = new CountDownLatch(1)
        val failure = new AtomicReference[Throwable]
        val algorithm = ParImportance.seeded(() => {
          val u = Universe.createNew()
          val target = Constant(true)(using "query", u)
          if (rejectForever) target.addCondition { _ => entered.countDown(); false }
          else target.addConstraint { _ => entered.countDown(); Thread.sleep(5); 1.0 }
          u
        }, 2, 1000000, 42L, "query")
        val caller = new Thread(() => { try algorithm.start() catch { case e: Throwable => failure.set(e) } })
        caller.start()
        try {
          entered.await(5, TimeUnit.SECONDS) shouldBe true
          caller.interrupt()
          caller.join(5000)
          caller.isAlive shouldBe false
          failure.get() shouldBe a[InterruptedException]
          algorithm.isActive shouldBe false
          workerThreads shouldBe empty
        } finally { caller.interrupt(); caller.join(5000) }
      }
    }
  }
}
