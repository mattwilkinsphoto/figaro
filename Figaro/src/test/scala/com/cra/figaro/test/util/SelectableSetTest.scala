/*
 * SelectableSetTest.scala   
 * Selectable set tests.
 * 
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   Jan 1, 2009
 * 
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 * 
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

/*
 * Additional Updates from our community
 * 
 * Paul Philips		May 23, 2017
 */

package com.cra.figaro.test.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.PrivateMethodTester
import math.log
import com.cra.figaro.util._
import com.cra.figaro.test._
import com.cra.figaro.test.tags.Performance

class SelectableSetTest extends AnyWordSpec with PrivateMethodTester with Matchers {
  "A SelectableSet" should {   
    "contain an element after insertion" in {
      val s = new HashSelectableSet[Int]
      s.add(1)
      s.contains(1) should be(true)
    }

    "not contain an element after removal" in {
      val s = new HashSelectableSet[Int]
      s.add(1)
      s.remove(1)
      s.contains(1) should be(false)
    }

    "convert to a list containing the elements it contains" in {
      val s = new HashSelectableSet[Int]
      // make sure to resize
      for { i <- 1 to 1000 } { s.add(i) }
      s.remove(2)
      val l = s.toList
      l.size should equal(999)
      for { i <- 1 to 1000 } { if (i != 2) l should contain(i) }
    }

    "return an element uniformly at random when selecting" in {
      val s = new HashSelectableSet[Int]
      s.add(1)
      s.add(2)
      s.add(3)
      s.add(6)
      var successes = 0
      val numTrials = 10000
      for { i <- 1 to numTrials } { if (s.select() == 2) successes += 1 }
      successes.toDouble / numTrials should be(0.25 +- 0.1)
    }

    "create a separate copy of itself when cloning" in {
      val s1 = new HashSelectableSet[Int]
      s1.add(1)
      val s2 = s1.clone
      s1.remove(1)
      s2.contains(1) should be(true)
    }

    "take roughly constant time for insertion" taggedAs (Performance) in {
      val small = 2000
      val large = 4000
      def insert(n: Int) = () => {
        val s = new HashSelectableSet[Int]
        for { j <- 1 to n } s.add(j)
      }
      val time1 = measureTime(insert(small), 20, 100)
      val time2 = measureTime(insert(large), 20, 100)
      // allow slack; if timing is not roughly constant this mark will probably be exceeded
      val slack = 1.2
      time2 / time1 should be < (large.toDouble / small * slack)
    }

    "take roughly constant time for removal" taggedAs (Performance) in {
      val small = 2000
      val large = 4000
      def remove(n: Int, s: SelectableSet[Int]) = () => {
        val copy = s.clone
        for { j <- 1 to n } copy.remove(j)
      }
      val s1 = new HashSelectableSet[Int]
      for { j <- 1 to small } s1.add(j)
      val s2 = new HashSelectableSet[Int]
      for { j <- 1 to large } s2.add(j)
      val time1 = measureTime(remove(small, s1), 20, 100)
      val time2 = measureTime(remove(large, s2), 20, 100)
      // allow slack
      val slack = 1.2
      time2 / time1 should be < (large.toDouble / small * slack)
    }

    "take roughly constant time for searching" taggedAs (Performance) in {
      val small = 2000
      val large = 4000
      def search(n: Int, s: SelectableSet[Int]) =
        () => for { j <- 1 to 2 * n } s.contains(j) // search for both present and absent elements
      val s1 = new HashSelectableSet[Int]
      for { j <- 1 to small } s1.add(j)
      val s2 = new HashSelectableSet[Int]
      for { j <- 1 to large } s2.add(j)
      val time1 = measureTime(search(small, s1), 20, 100)
      val time2 = measureTime(search(large, s2), 20, 100)
      // allow slack
      val slack = 1.2
      time2 / time1 should be < (large.toDouble / small * slack)
    }

    "take roughly log n time for selection" taggedAs (Performance) in {
      val small = 1024
      val large = 2048
      def select(n: Int, s: SelectableSet[Double]) =
        () => for { j <- 1 to n } s.select()
      val s1 = new HashSelectableSet[Double]
      for { j <- 1 to small } s1.add(random.nextDouble())
      val s2 = new HashSelectableSet[Double]
      for { j <- 1 to large } s2.add(random.nextDouble())
      val time1 = measureTime(select(small, s1), 20, 100)
      val time2 = measureTime(select(large, s2), 20, 100)
      // allow slack
      val slack = 1.1
      time2 / time1 should be < (large.toDouble / small * log(large) / log(small) * slack)
    }

    "enumerate complete contents across capacity transitions" in {
      // Timing moved to tools/collection-benchmark (ENG-01). Differences between
      // four short timings are not a correctness or asymptotic-complexity test.
      for (size <- List(1000, 2000, 3000, 4000)) {
        val s = new HashSelectableSet[Int]
        (0 until size).foreach(s.add)
        val before = s.toList
        before.size should be(size)
        before.toSet should be((0 until size).toSet)
        (0 until size by 3).foreach(s.remove)
        val after = s.toList
        after.toSet should be((0 until size).filter(_ % 3 != 0).toSet)
        after.distinct.size should be(after.size)
      }
    }
  }
}
