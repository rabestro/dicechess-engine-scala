import sbt.{given, *}
import org.scalajs.linker.interface.ESVersion
import scala.jdk.CollectionConverters.*

ThisBuild / organization := "lv.id.jc"
ThisBuild / version      := "1.12.1-SNAPSHOT"
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

// Fails the build when a coverage run produced no instrumentation metadata, instead of
// letting `coverageReport` warn and exit 0 with the threshold unenforced (#531).
lazy val coverageDataCheck = taskKey[Unit]("Verify the coverage run actually instrumented the code")

// The mirror image: refuse to publish a jar that IS coverage-instrumented.
lazy val assertNoCoverageInstrumentation =
  taskKey[Unit]("Fail if the packaged jar carries scoverage instrumentation")

// Prove the published engine jar carries no bench/arena classes (#564).
lazy val assertNoBenchClasses =
  taskKey[Unit]("Fail if the packaged jar carries dicechess/engine/bench classes")

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
    (ThisBuild / baseDirectory).value / platformDir / "src" / "test" / "scala",
    // Java-source test fixtures (currently only under jvm/): proving a facade is callable from
    // Java needs code the Scala compiler never gets to see. Nonexistent on js/wasm rows, so this
    // is a no-op there.
    (ThisBuild / baseDirectory).value / platformDir / "src" / "test" / "java"
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
    "com.monovore"  %% "decline"          % "2.6.2",
    "org.typelevel" %% "cats-core"        % "2.13.0",
    "io.circe"      %% "circe-core"       % "0.14.16",
    "io.circe"      %% "circe-generic"    % "0.14.16",
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
      coverageMinimumStmtTotal                          := 90,
      libraryDependencies += "org.jline"                 % "jline"       % "4.3.1",
      libraryDependencies += "com.microsoft.onnxruntime" % "onnxruntime" % "1.28.0",
      // sbt 2 defaults Test/exportJars to true (sbt 1 defaulted to false), packing
      // test resources into a CAS-cached jar. OnnxEvalSearchSpec/OnnxExpectimaxSearchSpec
      // resolve the bundled test model via `getClass.getResource(...).getPath`, which needs
      // a real filesystem path — a jar-embedded resource path breaks onnxruntime's native
      // file loader (`ORT_NO_SUCHFILE`). Restore the directory classpath for tests.
      Test / exportJars := false,
      // Backstop for #531: prove the coverage gate actually measured something.
      //
      // Coverage on Scala 3 is the compiler's own `-coverage-out:<dir>`, and the compiler
      // is what creates that directory and writes `scoverage.coverage` into it. sbt 2's
      // action cache can serve `compile` outright, in which case the compiler never runs,
      // the metadata is never written, and `coverageReport` merely warns "No coverage
      // data, skipping reports" — so `coverageFailOnMinimum` cannot fail and the gate
      // passes having measured nothing. `clean` does not help: the cache lives outside
      // `target/`. The coverage tasks avoid this by pointing the cache at a throwaway
      // repo-local directory (see mise.toml and the CI workflows); this task makes any
      // path that misses that loud instead of silent.
      //
      // Deliberately a plain, explicitly-invoked task rather than an override of `test`
      // or `coverageReport`: aggregation does not route through a subproject's overridden
      // tasks, which is exactly how the previous attempt at this silently did nothing.
      // `Def.uncached` is essential, not decoration: without it sbt 2 caches this task's
      // own successful result and replays it, so the check silently passes even once the
      // metadata is gone — the same cache-replay trap it exists to catch.
      coverageDataCheck := Def.uncached {
        val metadata = coverageDataDir.value / "scoverage-data" / "scoverage.coverage"
        if (!metadata.isFile)
          sys.error(
            s"""Coverage instrumentation metadata is missing: $metadata
               |
               |The compiler did not run, so nothing was measured and the coverage
               |threshold could not be enforced (see #531). sbt 2's build cache served the
               |compile, so the compiler never wrote it. Re-run against a cold cache:
               |
               |  sbt shutdown
               |  rm -rf target/covcache
               |  sbt -Dsbt.global.localcache="$$PWD/target/covcache" 'clean; coverage; testOnly *; rootJVM/coverageDataCheck; coverageReport'
               |
               |`mise run coverage` and `mise run check` already do exactly this. Note the
               |property is only read at server startup, so the `shutdown` is required.""".stripMargin
          )
        streams.value.log.info(s"Coverage instrumentation metadata present: $metadata")
      },
      // Refuse to publish a coverage-instrumented jar. sbt 2's thin client reuses the
      // server across workflow steps, so a release step inherits `set coverageEnabled :=
      // true` from the validation run — and if nothing forces a recompile it publishes the
      // instrumented classes. That artifact would carry `scala.runtime.coverage.Invoker`
      // calls and write measurement files at runtime in every consumer.
      // `Def.uncached` for the same reason as coverageDataCheck: otherwise sbt replays this
      // task's own earlier success and the check silently stops checking.
      assertNoCoverageInstrumentation := Def.uncached {
        // In sbt 2 `packageBin` yields an xsbti.HashedVirtualFileRef, not a File — it has to
        // go through `fileConverter` to get a real path.
        val jar    = fileConverter.value.toPath((Compile / packageBin).value).toFile
        val marker = "scala/runtime/coverage/Invoker"
        val zip    = new java.util.zip.ZipFile(jar)
        // `entries().asScala`, not `stream()`: the Java Stream's wildcard element type does
        // not unify with Scala 3's inference for the Predicate lambda.
        val hits =
          try
            zip.entries().asScala.count { entry =>
              entry.getName.endsWith(".class") && {
                val bytes = zip.getInputStream(entry).readAllBytes()
                new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains(marker)
              }
            }
          finally zip.close()
        if (hits > 0)
          sys.error(
            s"""$jar is coverage-instrumented: $hits class file(s) reference $marker.
               |
               |Publishing this would ship instrumented bytecode that writes scoverage
               |measurement files inside every consumer. Cause: sbt 2's thin client reuses
               |the server from an earlier step, so `coverageEnabled` is still set from the
               |validation run. Restart the server and rebuild before publishing:
               |
               |  sbt shutdown
               |  sbt 'clean; rootJVM/assertNoCoverageInstrumentation; rootJVM/publish'""".stripMargin
          )
        streams.value.log.info(s"No coverage instrumentation in ${jar.getName}")
      },
      assertNoBenchClasses := Def.uncached {
        val jar    = fileConverter.value.toPath((Compile / packageBin).value).toFile
        val marker = "dicechess/engine/bench/"
        val zip    = new java.util.zip.ZipFile(jar)
        val hits   =
          try
            zip.entries().asScala.count { entry =>
              entry.getName.startsWith(marker)
            }
          finally zip.close()
        if (hits > 0)
          sys.error(
            s"""$jar carries $hits bench class file(s) under $marker.
               |
               |The engine artifact must not ship bench/arena tooling to consumers (see #564).""".stripMargin
          )
        streams.value.log.info(s"No bench classes in ${jar.getName}")
      },
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
  .aggregate(rootJVM, rootJS, rootWasm, benchmark, arena)
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

lazy val arena = project
  .in(file("arena"))
  .dependsOn(rootJVM)
  .settings(commonSettings)
  .settings(
    name                     := "dicechess-arena",
    publish / skip           := true,
    coverageMinimumStmtTotal := 70,
    coverageFailOnMinimum    := true,
    coverageDataCheck        := Def.uncached {
      val metadata = coverageDataDir.value / "scoverage-data" / "scoverage.coverage"
      if (!metadata.isFile)
        sys.error(
          s"""Coverage instrumentation metadata is missing: $metadata
             |
             |The compiler did not run, so nothing was measured and the coverage
             |threshold could not be enforced (see #531). sbt 2's build cache served the
             |compile, so the compiler never wrote it. Re-run against a cold cache:
             |
             |  sbt shutdown
             |  rm -rf target/covcache
             |  sbt -Dsbt.global.localcache="$$PWD/target/covcache" 'clean; coverage; testOnly *; arena/coverageDataCheck; coverageReport'""".stripMargin
        )
      streams.value.log.info(s"Coverage instrumentation metadata present: $metadata")
    }
  )
