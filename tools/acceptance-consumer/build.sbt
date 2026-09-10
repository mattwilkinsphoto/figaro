import sbt.{given, *}
import Keys.*

scalaVersion := "3.9.0"
name := "figaro-acceptance-consumer"
publish / skip := true
libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % "6.1.0"
// When testing the release bundle, exclude Ivy local so a cached publishLocal
// cannot hide a broken Maven layout or POM.
externalResolvers ~= { defaults =>
  sys.env.get("FIGARO_RELEASE_MAVEN_ROOT").map { path =>
    require(file(path).isDirectory, "Release Maven directory does not exist")
    Seq("figaro-release" at file(path).toURI.toString, Resolver.mavenCentral)
  }.getOrElse(defaults)
}
Compile / scalacOptions ++= Seq("-release:17", "-deprecation", "-Wconf:cat=deprecation:error")
Compile / run / fork := true
Compile / run / javaOptions ++= Seq("-Xmx1G", "-Xss6M")
