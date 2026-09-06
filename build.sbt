//
// build.sbt
// Figaro SBT build script
//
// Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
// See LICENSE and FigaroAttributions.txt.
//

import sbt.{given, *}
import Keys.*
import sbtassembly.AssemblyPlugin.autoImport.*

// sbt uses this fixed ZIP-entry timestamp for package tasks. Declaring it here,
// together with sbt-assembly's stable ordering, makes the release policy explicit.
val reproducibleTimestamp = 1262304000000L
packageTimestamp := Some(reproducibleTimestamp)
assemblyRepeatableBuild := true

lazy val DetTest = config("det").extend(Test)
lazy val NonDetTest = config("nonDet").extend(Test)

def readManifest(path: String): java.util.jar.Manifest = {
  val stream = new java.io.FileInputStream(file(path))
  try new java.util.jar.Manifest(stream)
  finally stream.close()
}

def legalMappings(repositoryRoot: File, converter: xsbti.FileConverter): Seq[(xsbti.HashedVirtualFileRef, String)] = Seq(
  repositoryRoot / "LICENSE" -> "META-INF/LICENSE",
  repositoryRoot / "FigaroAttributions.txt" -> "META-INF/FigaroAttributions.txt"
).map { case (source, destination) => converter.toVirtualFile(source.toPath) -> destination }

// Read external legal/manifest inputs on each invocation; parent package tasks
// still cache using the resulting content hashes and manifest attributes.
lazy val legalSettings = Seq(
  Compile / packageBin / packageOptions := Def.uncached {
    Seq(Package.JarManifest(readManifest((baseDirectory.value / "META-INF" / "MANIFEST.MF").getPath)))
  },
  Compile / packageBin / mappings ++= Def.uncached { legalMappings(baseDirectory.value.getParentFile, fileConverter.value) },
  Compile / packageSrc / mappings ++= Def.uncached { legalMappings(baseDirectory.value.getParentFile, fileConverter.value) },
  Compile / packageDoc / mappings ++= Def.uncached { legalMappings(baseDirectory.value.getParentFile, fileConverter.value) }
)

lazy val figaroSettings = Seq(
  organization := "io.github.mattwilkinsphoto",
  description := "Figaro: a language for probabilistic programming",
  version := "6.0.0-modern.6-SNAPSHOT",
  scalaVersion := "3.9.0",
  crossScalaVersions := Seq("3.9.0"),
  crossPaths := true,
  publishMavenStyle := true,
  homepage := Some(uri("https://github.com/mattwilkinsphoto/figaro")),
  licenses := Seq("Figaro License" -> uri("https://github.com/charles-river-analytics/figaro/blob/master/LICENSE")),
  scmInfo := Some(
    ScmInfo(
      uri("https://github.com/mattwilkinsphoto/figaro"),
      "scm:git:https://github.com/mattwilkinsphoto/figaro.git"
    )
  ),
  developers := List(
    Developer(
      id = "cra-figaro",
      name = "Figaro contributors",
      email = "figaro@cra.com",
      url = uri("https://github.com/charles-river-analytics/figaro")
    )
  ),
  Compile / scalacOptions ++= Seq(
    "-release:17",
    // Keep the repository's brace-delimited syntax without indentation semantics.
    "-no-indent",
    "-feature",
    "-deprecation",
    // Retired language/library APIs must not return through library, examples, or tests.
    "-Wconf:cat=deprecation:error",
    "-language:postfixOps"
  ),
  Compile / javacOptions ++= Seq("--release", "17")
)

lazy val root = project
  .in(file("."))
  .settings(figaroSettings)
  .settings(name := "figaro-root", publish / skip := true)
  .dependsOn(figaro, examples)
  .aggregate(figaro, examples)

lazy val figaro = project
  .in(file("Figaro"))
  .configs(DetTest, NonDetTest)
  .settings(figaroSettings)
  .settings(legalSettings)
  .settings(
    Test / fork := true,
    Test / javaOptions += "-Xmx6G",
    Compile / run / fork := true,
    Compile / run / baseDirectory := baseDirectory.value,
    Compile / run / javaOptions += "-Xmx6G",
    Test / parallelExecution := false,
    Test / testOptions += Tests.Argument("-oD"),
    assembly / test := sbt.protocol.testing.TestResult.Empty,
    assembly / packageOptions += Package.FixedTimestamp(Some(reproducibleTimestamp)),
    assembly / assemblyJarName := s"figaro_${scalaBinaryVersion.value}-${version.value}-fat.jar",
    assembly / assemblyOption := (assembly / assemblyOption).value.withIncludeScala(false),
    logBuffered := false,
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-math3" % "3.6.1",
      "io.github.argonaut-io" %% "argonaut" % "6.3.13",
      "org.scala-lang.modules" %% "scala-swing" % "3.0.0",
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.2.0",
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    )
  )
  .settings(inConfig(DetTest)(Defaults.testTasks))
  .settings(DetTest / testOptions := Seq(Tests.Argument("-l", "com.cra.figaro.test.nonDeterministic")))
  .settings(inConfig(NonDetTest)(Defaults.testTasks))
  .settings(NonDetTest / testOptions := Seq(Tests.Argument("-n", "com.cra.figaro.test.nonDeterministic")))

lazy val examples = project
  .in(file("FigaroExamples"))
  .dependsOn(figaro)
  .settings(figaroSettings)
  .settings(legalSettings)
