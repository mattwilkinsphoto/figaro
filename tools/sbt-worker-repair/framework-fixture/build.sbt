scalaVersion := "3.9.0"
libraryDependencies += "org.scala-sbt" % "test-interface" % "1.0" % Test
Test / fork := true
Test / parallelExecution := false
Test / javaOptions ++= Seq("-Xmx512m", "-Dfigaro.fixture.worker=true")
Test / testFrameworks := Seq(new TestFramework("FrameworkFailureFramework"))
