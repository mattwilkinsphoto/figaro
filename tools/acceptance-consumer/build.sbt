import sbt.{given, *}
import Keys.*

scalaVersion := "3.9.0"
name := "figaro-acceptance-consumer"
publish / skip := true
libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % "6.0.0-modern.11-SNAPSHOT"
Compile / scalacOptions ++= Seq("-release:17", "-deprecation", "-Wconf:cat=deprecation:error")
Compile / run / fork := true
Compile / run / javaOptions ++= Seq("-Xmx1G", "-Xss6M")
