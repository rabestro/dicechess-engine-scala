import sbt.{given, *}
import org.scalajs.linker.interface.ESVersion

ThisBuild / organization := "lv.id.jc"
ThisBuild / version      := "1.11.3-SNAPSHOT"
ThisBuild / scalaVersion := "3.8.4"

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

val ScalaV = "3.8.4"

// projectMatrix's default layout is src/main/scala + src/main/scala-<platform-suffix>,
// keyed off the row's own (synthetic, .sbt/matrix/<id>) base directory. Pin every row
// back to this repo's crossProject-era physical layout (shared/ + jvm/ + js/) instead,
// so no source file has to move.
def layout(platformDir: String) = Seq(
  Compile / unmanagedSourceDirectories := Seq(
    (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala",
    (ThisBuild / baseDirectory).value / platformDir / "src" / "main" / "scala"
  ),
  Test / unmanagedSourceDirectories := Seq(
    (ThisBuild / baseDirectory).value / "shared" / "src" / "test" / "scala",
    (ThisBuild / baseDirectory).value / platformDir / "src" / "test" / "scala"
  ),
  Compile / unmanagedResourceDirectories := Seq(
    (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "resources",
    (ThisBuild / baseDirectory).value / platformDir / "src" / "main" / "resources"
  ),
  Test / unmanagedResourceDirectories := Seq(
    (ThisBuild / baseDirectory).value / "shared" / "src" / "test" / "resources",
    (ThisBuild / baseDirectory).value / platformDir / "src" / "test" / "resources"
  )
)

lazy val commonSettings = Seq(
  name := "dicechess-engine-scala",
  libraryDependencies ++= Seq(
    "com.monovore"  %% "decline"          % "2.5.0",
    "org.typelevel" %% "cats-core"        % "2.13.0",
    "io.circe"      %% "circe-core"       % "0.14.15",
    "io.circe"      %% "circe-generic"    % "0.14.15",
    "io.circe"      %% "circe-parser"     % "0.14.16",
    "org.scalameta" %% "munit"            % "1.3.0" % Test,
    "org.scalameta" %% "munit-scalacheck" % "1.3.0" % Test
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

lazy val root = (projectMatrix in file("."))
  .settings(commonSettings)
  // Omitting VirtualAxis.jvm here is deliberate: it's what keeps the JVM row's
  // project id as `rootJVM` instead of collapsing it to `root` (see rootJVM/rootJS below).
  .defaultAxes(VirtualAxis.scalaABIVersion(ScalaV))
  .jvmPlatform(
    scalaVersions = Seq(ScalaV),
    settings = layout("jvm") ++ Seq(
      // JVM-specific settings
      libraryDependencies += "org.jline"                 % "jline"       % "4.3.1",
      libraryDependencies += "com.microsoft.onnxruntime" % "onnxruntime" % "1.28.0",
      // sbt 2 defaults Test/exportJars to true (sbt 1 defaulted to false), packing
      // test resources into a CAS-cached jar. OnnxEvalSearchSpec/OnnxExpectimaxSearchSpec
      // resolve the bundled test model via `getClass.getResource(...).getPath`, which needs
      // a real filesystem path — a jar-embedded resource path breaks onnxruntime's native
      // file loader (`ORT_NO_SUCHFILE`). Restore the directory classpath for tests.
      Test / exportJars := false,
      // Pre-existing scoverage runtime race (scoverage/sbt-scoverage#228-like): the
      // instrumented runtime lazily creates scoverage-data/ via a non-atomic
      // check-then-mkdir on first measurement write, and that can lose a race against
      // test startup, throwing FileNotFoundException. Force the directory to exist
      // before the test JVM(s) start.
      Test / test := (Test / test)
        .dependsOn(Def.task {
          IO.createDirectory(coverageDataDir.value / "scoverage-data")
        })
        .evaluated,
      Test / testOnly := (Test / testOnly)
        .dependsOn(Def.task {
          IO.createDirectory(coverageDataDir.value / "scoverage-data")
        })
        .evaluated,
      Test / testQuick := (Test / testQuick)
        .dependsOn(Def.task {
          IO.createDirectory(coverageDataDir.value / "scoverage-data")
        })
        .evaluated,
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
  )
  .jsPlatform(
    scalaVersions = Seq(ScalaV),
    settings = layout("js") ++ Seq(
      // Scala.js-specific settings
      coverageEnabled                 := false, // Disable coverage for JS to avoid linking errors
      scalaJSUseMainModuleInitializer := false, // We'll expose functions via @JSExportTopLevel
      scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }
    )
  )

lazy val rootJVM = root.jvm(ScalaV)
lazy val rootJS  = root.js(ScalaV)

// projectMatrix rows never claim the literal build-root directory — they live under
// .sbt/matrix/<id> — so without this, sbt auto-generates its own empty synthetic
// project at "." that aggregates nothing, and bare `sbt test`/`coverage` would
// silently report zero tests. Reclaim "." explicitly.
//
// The aggregate list must stay exhaustive: under sbt 1 nothing claimed "." either, so
// sbt's own synthetic root aggregated EVERY project in the build — verified against the
// last green sbt 1 CI run, which compiled jvm + js + .wasm + benchmark and reported three
// test totals (527 JVM, 408 JS, 408 Wasm). Dropping a project here silently removes it
// from the `test`/`coverage` gate rather than failing, so keep this in sync when adding
// a project.
lazy val dicechessEngineScala = (project in file("."))
  .aggregate(rootJVM, rootJS, rootWasm, benchmark)
  .settings(
    // Must differ from rootJVM/rootJS's `name` ("dicechess-engine-scala") — sbt 2's
    // shared target/out/ layout keys output directories by project name, not base
    // directory, and a name collision here fails the whole build load with
    // "Overlapping output directories".
    name           := "dicechess-engine-scala-aggregate",
    publish / skip := true
  )

lazy val rootWasm = project
  .in(file(".wasm"))
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
  .settings(layout("js"))
  .settings(
    name                            := "dicechess-engine-scala-wasm",
    coverageEnabled                 := false,
    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= {
      // Scala.js 1.22 moved WebAssembly out of "experimental" (now set via
      // ESFeatures) and its backend requires ECMAScript 2022 or later.
      _.withModuleKind(ModuleKind.ESModule)
        .withESFeatures(_.withESVersion(ESVersion.ES2022).withUseWebAssembly(true))
    }
  )

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
