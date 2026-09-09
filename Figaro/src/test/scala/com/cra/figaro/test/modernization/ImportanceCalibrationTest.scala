package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ImportanceCalibrationTest extends AnyWordSpec with Matchers {
  "The calibration protocol" should {
    "use the global-ratio batch formula and preserve unit/log-weight shifts" in {
      val values=Vector.fill(20)(1.0)++Vector.fill(20)(-1.0)
      val (error,count,_)=ImportanceCalibrationStudy.assessment(Vector.fill(40)(0.0),values,0)
      error shouldBe (math.sqrt(1.0/19) +- 1e-14)
      count shouldBe (40.0 +- 1e-12)
      val shifted=ImportanceCalibrationStudy.assessment(Vector.fill(40)(1000.0),values.map(_*3+7),7)
      shifted._1 shouldBe (3*error +- 1e-13)
      shifted._2 shouldBe (count +- 1e-12)
    }
    "retain zero-variation and deliberately missed-mode controls without claiming precision" in {
      val result=ImportanceCalibrationStudy.assessment(Vector.fill(40)(0.0),Vector.fill(40)(1.0),1)
      result._1 shouldBe 0.0
      result._2 shouldBe 0.0
      result._3 shouldBe None
      val cases=ImportanceCalibrationStudy.cases
      cases.size shouldBe 11
      cases.map(_.id).distinct.size shouldBe 11
      cases.count(_.pilot) shouldBe 2
      cases.find(_.id=="missed-mode").get.reference shouldBe .5
      cases.find(_.id=="rare").get.reference shouldBe cases.find(_.id=="rare-tilted").get.reference
    }
  }
}
