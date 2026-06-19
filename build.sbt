ThisBuild / organization := "lv.id.jc"
ThisBuild / version      := "1.3.0-SNAPSHOT"
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
    id = "rabestro",
    name = "Jegors Čemisovs",
    email = "jegors.cemisovs@gmail.com",
    url = url("https://jc.id.lv")
  )
)

// Publishing (JVM artifact → GitHub Packages Maven registry).
// CI overrides the version with the clean tag value, e.g. `set ThisBuild / version := "1.2.4"`.
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / publishTo     := Some(
  "GitHub Packages" at "https://maven.pkg.github.com/rabestro/dicechess-engine-scala"
)
ThisBuild / credentials ++= (for {
  user  <- sys.env.get("GITHUB_ACTOR")
  token <- sys.env.get("GITHUB_TOKEN")
} yield Credentials("GitHub Package Registry", "maven.pkg.github.com", user, token)).toSeq

lazy val root = crossProject(JSPlatform, JVMPlatform)
  .in(file("."))
  .settings(
    name := "dicechess-engine-scala",
    libraryDependencies ++= Seq(
      "com.monovore"  %%% "decline"   % "2.5.0",
      "org.typelevel" %%% "cats-core" % "2.13.0",

      // JSON library (Circe) - using %%% for cross-platform support
      "io.circe" %%% "circe-core"    % "0.14.15",
      "io.circe" %%% "circe-generic" % "0.14.15",
      "io.circe" %%% "circe-parser"  % "0.14.15",

      // Testing framework
      "org.scalameta" %%% "munit"            % "1.3.0" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.3.0" % Test
    ),
    semanticdbEnabled        := true,
    semanticdbVersion        := scalafixSemanticdb.revision,
    coverageExcludedFiles    := ".*Main\\.scala",
    coverageMinimumStmtTotal := 85,
    coverageFailOnMinimum    := true,
    scalacOptions ++= Seq(
      "-Werror",                  // Fail the compilation if there are any warnings
      "-Wunused:all",             // Fail on unused imports, privates, locals, and implicits
      "-language:strictEquality", // Prevent comparing different types
      "-Yexplicit-nulls",         // Make null explicit
      "-explain",                 // Explain type errors in more detail
      "-feature",                 // Emit warning and location for usages of features that should be imported explicitly
      "-deprecation"              // Emit warning and location for usages of deprecated APIs
    )
  )
  .jvmSettings(
    // JVM-specific settings
    libraryDependencies += "org.jline" % "jline" % "3.26.3",
    Compile / doc / scalacOptions ++= Seq(
      "-project",
      name.value,
      "-project-version",
      version.value,
      "-project-footer",
      "Dice Chess Engine",
      "-project-logo",
      "docs/public/favicon.svg",
      "-source-links:src/main/scala=https://github.com/rabestro/dicechess-engine-scala/blob/main/src/main/scala€{FILE_PATH}.scala#L€{LINE}",
      "-social-links:github:https://github.com/rabestro/dicechess-engine-scala",
      "-doc-root-content",
      (baseDirectory.value / "README.md").getAbsolutePath,
      "-groups",
      "-author",
      "-snippet-compiler:compile"
    )
  )
  .jsSettings(
    // Scala.js-specific settings
    coverageEnabled                 := false, // Disable coverage for JS to avoid linking errors
    scalaJSUseMainModuleInitializer := false, // We'll expose functions via @JSExportTopLevel
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }
  )

lazy val rootJVM = root.jvm
lazy val rootJS  = root.js

lazy val benchmark = project
  .in(file("benchmark"))
  .dependsOn(rootJVM)
  .enablePlugins(JmhPlugin)
  .settings(
    name                    := "dicechess-benchmark",
    Compile / doc / sources := Seq.empty,
    coverageEnabled         := false,
    publish / skip          := true,
    scalacOptions -= "-Werror"
  )
