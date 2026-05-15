ThisBuild / organization := "lv.id.jc"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.8.3"

ThisBuild / description := "High-performance Dice Chess engine and probability calculator in Scala 3."
ThisBuild / homepage    := Some(url("https://jc.id.lv/dicechess-engine-scala/"))
ThisBuild / licenses    := List("AGPL-3.0" -> url("https://www.gnu.org/licenses/agpl-3.0.txt"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/rabestro/dicechess-engine-scala"),
    "scm:git@github.com:rabestro/dicechess-engine-scala.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id    = "rabestro",
    name  = "Jegors Čemisovs",
    email = "jegors.cemisovs@gmail.com",
    url   = url("https://jc.id.lv")
  )
)

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
    ),
    Compile / doc / scalacOptions ++= Seq(
      "-project",         name.value,
      "-project-version", version.value,
      "-project-footer",  "Dice Chess Engine",
      "-project-logo",    "docs/public/favicon.svg",
      "-source-links:src/main/scala=https://github.com/rabestro/dicechess-engine-scala/blob/main/src/main/scala€{FILE_PATH}.scala#L€{LINE}",
      "-social-links:github://https://github.com/rabestro/dicechess-engine-scala",
      "-doc-root-content", (baseDirectory.value / "README.md").getAbsolutePath,
      "-groups",
      "-author",
      "-snippet-compiler:compile"
    )
  )
