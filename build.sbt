thisBuild / version      := "0.1.0-SNAPSHOT"
thisBuild / organization := "com.dicechess"
thisBuild / scalaVersion := "3.3.3"

lazy val root = (project in file("."))
  .settings(
    name := "dicechess-engine-scala",
    libraryDependencies ++= Seq(
      // JSON library (Circe)
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6",
      
      // Testing framework
      "org.scalameta" %% "munit" % "0.7.29" % Test
    )
  )
