import org.scalatest.funsuite.AnyFunSuite

// Deliberate failure fixtures. Never included in Figaro's test/source projects.
class WorkerEvents extends AnyFunSuite {
  (1 to 100).foreach { i => test(s"event-$i") { assert(i > 0) } }
  test("sentinel assertion failure") { fail("SENTINEL_ASSERTION_FAILURE") }
}

class WorkerExit extends AnyFunSuite {
  test("sentinel abnormal worker exit") { System.exit(17) }
}
