ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.1.3"

val fs2Version = "3.2.7"

lazy val root = project
  .in(file("."))
  .settings(
    name := "netflow-collector-fs2",

    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.3.12",
      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-io" % fs2Version,
      "co.fs2" %% "fs2-reactive-streams" % fs2Version,
      "com.github.fd4s" %% "fs2-kafka" % "2.5.0-M3",

      "org.typelevel" %% "log4cats-slf4j"   % "2.3.1",

      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "com.disneystreaming" %% "weaver-cats" % "0.7.12" % Test
    )

  )

testFrameworks += new TestFramework("weaver.framework.CatsEffect")