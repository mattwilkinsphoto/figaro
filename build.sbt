//
// build.sbt
// Figaro SBT build script
//
// Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
// See LICENSE and FigaroAttributions.txt.
//

import sbt._
import Keys._
import sbt.Package.ManifestAttributes
import sbtassembly.AssemblyPlugin.autoImport._

name := "figaro-root"

// sbt uses this fixed ZIP-entry timestamp for package tasks. Declaring it here,
// together with sbt-assembly's stable ordering, makes the release policy explicit.
val reproducibleTimestamp = 1262304000000L
ThisBuild / packageTimestamp := Some(reproducibleTimestamp)
ThisBuild / assemblyRepeatableBuild := true

lazy val DetTest = config("det").extend(Test)
lazy val NonDetTest = config("nonDet").extend(Test)

def readManifest(path: String): java.util.jar.Manifest = {
  val stream = new java.io.FileInputStream(file(path))
  try new java.util.jar.Manifest(stream)
  finally stream.close()
}

def legalMappings(repositoryRoot: File): Seq[(File, String)] = Seq(
  repositoryRoot / "LICENSE" -> "META-INF/LICENSE",
  repositoryRoot / "FigaroAttributions.txt" -> "META-INF/FigaroAttributions.txt"
)

lazy val figaroManifest = readManifest("Figaro/META-INF/MANIFEST.MF")
lazy val examplesManifest = readManifest("FigaroExamples/META-INF/MANIFEST.MF")

lazy val figaroSettings = Seq(
  organization := "io.github.mattwilkinsphoto",
  description := "Figaro: a language for probabilistic programming",
  version := "5.0.0-modern.2-SNAPSHOT",
  scalaVersion := "2.13.18",
  crossScalaVersions := Seq("2.13.18"),
  crossPaths := true,
  publishMavenStyle := true,
  homepage := Some(url("https://github.com/mattwilkinsphoto/figaro")),
  licenses := Seq("Figaro License" -> url("https://github.com/charles-river-analytics/figaro/blob/master/LICENSE")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/mattwilkinsphoto/figaro"),
      "scm:git:https://github.com/mattwilkinsphoto/figaro.git"
    )
  ),
  developers := List(
    Developer(
      id = "cra-figaro",
      name = "Figaro contributors",
      email = "figaro@cra.com",
      url = url("https://github.com/charles-river-analytics/figaro")
    )
  ),
  Compile / scalacOptions ++= Seq(
    "-release:17",
    "-feature",
    "-language:existentials",
    "-deprecation",
    "-language:postfixOps"
  ),
  Compile / javacOptions ++= Seq("--release", "17")
)

lazy val root = project
  .in(file("."))
  .settings(figaroSettings)
  .settings(publish / skip := true)
  .dependsOn(figaro, examples)
  .aggregate(figaro, examples)

lazy val figaro = project
  .in(file("Figaro"))
  .configs(DetTest, NonDetTest)
  .settings(figaroSettings)
  .settings(
    Compile / packageBin / packageOptions := Seq(Package.JarManifest(figaroManifest)),
    Compile / packageBin / mappings ++= legalMappings(baseDirectory.value.getParentFile),
    Compile / packageSrc / mappings ++= legalMappings(baseDirectory.value.getParentFile),
    Compile / packageDoc / mappings ++= legalMappings(baseDirectory.value.getParentFile),
    Test / fork := true,
    Test / javaOptions += "-Xmx6G",
    Compile / run / fork := true,
    Compile / run / javaOptions += "-Xmx6G",
    Test / parallelExecution := false,
    Test / testOptions += Tests.Argument("-oD"),
    assembly / test := {},
    assembly / packageOptions += Package.FixedTimestamp(Some(reproducibleTimestamp)),
    assembly / assemblyJarName := s"figaro_${scalaBinaryVersion.value}-${version.value}-fat.jar",
    assembly / assemblyOption := (assembly / assemblyOption).value.withIncludeScala(false),
    logBuffered := false,
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.apache.commons" % "commons-math3" % "3.6.1",
      "io.github.argonaut-io" %% "argonaut" % "6.3.13",
      "org.scala-lang.modules" %% "scala-swing" % "2.1.1",
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.2.0",
      "org.scalatest" %% "scalatest" % "3.1.0" % Test
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
  .settings(
    Compile / packageBin / packageOptions := Seq(Package.JarManifest(examplesManifest)),
    Compile / packageBin / mappings ++= legalMappings(baseDirectory.value.getParentFile),
    Compile / packageSrc / mappings ++= legalMappings(baseDirectory.value.getParentFile),
    Compile / packageDoc / mappings ++= legalMappings(baseDirectory.value.getParentFile)
  )
