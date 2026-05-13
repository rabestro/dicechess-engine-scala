ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.evolution"
ThisBuild / scalaVersion := "3.8.3"

lazy val root = (project in file("."))
  .settings(
    name := "dicechess-engine-scala",
    libraryDependencies ++= Seq(
      // JSON library (Circe)
      "io.circe" %% "circe-core" % "0.14.15",
      "io.circe" %% "circe-generic" % "0.14.15",
      "io.circe" %% "circe-parser" % "0.14.15",

      // Testing framework
      "org.scalameta" %% "munit" % "1.3.0" % Test
    ),
    scalacOptions ++= Seq(
      "-Werror",           // Fail the compilation if there are any warnings
      "-Wunused:all",      // Fail on unused imports, privates, locals, and implicits
      "-explain",          // Explain type errors in more detail
      "-feature",          // Emit warning and location for usages of features that should be imported explicitly
      "-deprecation"       // Emit warning and location for usages of deprecated APIs
    )
  )
