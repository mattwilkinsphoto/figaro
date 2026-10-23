scalaVersion := "3.9.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test
Test / fork := true
Test / parallelExecution := false
Test / javaOptions += "-Xmx512m"
Test / testOptions += Tests.Argument("-oD")
