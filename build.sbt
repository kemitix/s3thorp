val commonSettings = Seq(
  version := "DEV-SNAPSHOT",
  organization := "net.kemitix",
  scalaVersion := "2.12.8",
  test in assembly := {}
)

val applicationSettings = Seq(
  name := "thorp",
)
val testDependencies = Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.8" % Test,
    "org.scalamock" %% "scalamock" % "4.3.0" % Test
  )
)
val commandLineParsing = Seq(
  libraryDependencies ++= Seq(
    "com.github.scopt" %% "scopt" % "4.0.0-RC2"
  )
)
val awsSdkDependencies = Seq(
  libraryDependencies ++= Seq(
    "com.amazonaws" % "aws-java-sdk-s3" % "1.11.582",
    // override the versions AWS uses, which is they do to preserve Java 6 compatibility
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.9",
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % "2.9.9"
  )
)
val catsEffectsSettings = Seq(
  libraryDependencies ++=  Seq(
    "org.typelevel" %% "cats-effect" % "1.3.1"
  ),
  // recommended for cats-effects
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-language:postfixOps",
    "-language:higherKinds",
    "-Ypartial-unification")
)

// cli -> thorp-lib -> storage-aws -> core -> storage-api -> domain

lazy val thorp = (project in file("."))
  .settings(commonSettings)
  .aggregate(cli, `thorp-lib`, `storage-aws`, core, `storage-api`, domain)

lazy val cli = (project in file("cli"))
  .settings(commonSettings)
  .settings(mainClass in assembly := Some("net.kemitix.thorp.cli.Main"))
  .settings(applicationSettings)
  .settings(commandLineParsing)
  .settings(testDependencies)
  .dependsOn(`thorp-lib`)

lazy val `thorp-lib` = (project in file("thorp-lib"))
  .settings(commonSettings)
  .settings(assemblyJarName in assembly := "thorp-lib.jar")
  .dependsOn(`storage-aws`)

lazy val `storage-aws` = (project in file("storage-aws"))
  .settings(commonSettings)
  .settings(assemblyJarName in assembly := "storage-aws.jar")
  .settings(awsSdkDependencies)
  .settings(testDependencies)
  .dependsOn(core % "compile->compile;test->test")

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(assemblyJarName in assembly := "core.jar")
  .settings(testDependencies)
  .dependsOn(`storage-api`)

lazy val `storage-api` = (project in file("storage-api"))
  .settings(commonSettings)
  .settings(assemblyJarName in assembly := "storage-api.jar")
  .dependsOn(domain)

lazy val domain = (project in file("domain"))
  .settings(commonSettings)
  .settings(assemblyJarName in assembly := "domain.jar")
  .settings(catsEffectsSettings)
  .settings(testDependencies)
