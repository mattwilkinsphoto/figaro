import sbt.{given, *}
import Keys.*

scalaVersion := "3.9.0"
name := "figaro-acceptance-consumer"
publish / skip := true
libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % sys.env.getOrElse("FIGARO_VERSION", "6.1.0")
// Keep the immutable 6.1 Central consumer runnable without resolving newer APIs.
Compile / unmanagedSourceDirectories ++= {
  if (sys.env.get("FIGARO_VERSION").exists(_.startsWith("7."))) Seq(baseDirectory.value / "src" / "integration" / "scala")
  else Seq.empty
}
Compile / unmanagedSourceDirectories ++= {
  if (sys.env.get("FIGARO_CAPABILITY_CHECK").contains("true")) Seq(baseDirectory.value / "src" / "capability" / "scala")
  else Seq.empty
}
// When testing the release bundle, exclude Ivy local so a cached publishLocal
// cannot hide a broken Maven layout or POM.
externalResolvers ~= { defaults =>
  if (sys.env.get("FIGARO_CENTRAL_ONLY").contains("true")) Seq(Resolver.mavenCentral)
  else sys.env.get("FIGARO_RELEASE_MAVEN_ROOT").map { path =>
    require(file(path).isDirectory, "Release Maven directory does not exist")
    Seq("figaro-release" at file(path).toURI.toString, Resolver.mavenCentral)
  }.getOrElse(defaults)
}
Compile / scalacOptions ++= Seq("-release:17", "-deprecation", "-Wconf:cat=deprecation:error")
Compile / run / fork := true
Compile / run / javaOptions ++= Seq("-Xmx1G", "-Xss6M")
