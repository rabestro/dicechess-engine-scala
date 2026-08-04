---
title: Maven Artifact & JVM Integration
description: How the engine is published as a JVM library to the GitHub Packages Maven registry and how downstream Scala, Java, and Kotlin projects consume it.
---

The engine is the **single source of truth for Dice Chess rules** across the ecosystem. JVM
backends — first of all [dicechess-analytics](https://github.com/rabestro/dicechess-analytics)
(the Scala 3 analytics backend) — consume it as a regular Maven dependency instead of
re-implementing game logic.

Non-Scala JVM callers (Java, Kotlin) bind to a dedicated facade rather than to the Scala API
directly — see [Consuming from Java or Kotlin](#consuming-from-java-or-kotlin) below.

Every release publishes the JVM artifact alongside the NPM package:

| Coordinate | Value |
| :--- | :--- |
| Group ID | `lv.id.jc` |
| Artifact ID | `dicechess-engine-scala_3` |
| Registry | [GitHub Packages Maven](https://github.com/rabestro/dicechess-engine-scala/packages) |

---

## Consuming the Artifact (sbt)

GitHub Packages requires authentication **even for public packages**, so consumers need a
token with the `read:packages` scope. Locally the `GITHUB_ACTOR` / `GITHUB_TOKEN` environment
variables are used; in GitHub Actions the built-in `GITHUB_TOKEN` works as-is.

```scala
resolvers += "GitHub Packages (dicechess-engine)" at
  "https://maven.pkg.github.com/rabestro/dicechess-engine-scala"

credentials ++= (for {
  user  <- sys.env.get("GITHUB_ACTOR")
  token <- sys.env.get("GITHUB_TOKEN")
} yield Credentials("GitHub Package Registry", "maven.pkg.github.com", user, token)).toSeq

libraryDependencies += "lv.id.jc" %% "dicechess-engine-scala" % "<latest release>"
```

---

## Consuming from Java or Kotlin

Java and Kotlin callers depend on the same artifact, but bind to
[`dicechess.engine.jvmapi.JvmApi`](/dicechess-engine-scala/architecture/jvm-api/) rather than to the
Scala API. The Scala surface leans on constructs that do not survive the language boundary
intact — `Either` returns, extension methods (which compile onto synthetic `$package` classes with
no ordinary entry point), and opaque types like `Move` that erase to `int`, turning a
`List[List[Move]]` into an unchecked `List[List[Object]]` of boxed integers. `JvmApi` exposes only
`java.util` types, primitives, and opaque handles, so none of that reaches the caller.

Note the `_3` suffix in the artifact ID: Maven has no equivalent of sbt's `%%` operator, so the
Scala binary-version suffix has to be spelled out.

```xml
<repositories>
    <repository>
        <id>github-dicechess-engine</id>
        <url>https://maven.pkg.github.com/rabestro/dicechess-engine-scala</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>lv.id.jc</groupId>
        <artifactId>dicechess-engine-scala_3</artifactId>
        <version><!-- latest release --></version>
    </dependency>
</dependencies>
```

Authentication works the same way as for sbt, but Maven reads it from `~/.m2/settings.xml`
rather than the environment:

```xml
<settings>
    <servers>
        <server>
            <id>github-dicechess-engine</id>
            <username><!-- GitHub username --></username>
            <password><!-- token with read:packages --></password>
        </server>
    </servers>
</settings>
```

The `<id>` must match the `<repository>` id exactly, or Maven sends the request unauthenticated
and GitHub Packages answers `401 Unauthorized`.

[dicechess-bot-java](https://github.com/rabestro/dicechess-bot-java) is the reference consumer.

---

## Local Development Against Unreleased Changes

When a downstream project needs engine changes that are not released yet, publish the JVM
artifact to the local Ivy repository:

```bash
mise run publish:local
```

This publishes the current `-SNAPSHOT` version to `~/.ivy2/local`, where sbt resolves it
before any remote registry.

Maven does not read `~/.ivy2/local`, so a Maven-built consumer needs the artifact in the local
Maven repository instead:

```bash
sbt rootJVM/publishM2
```

That writes to `~/.m2/repository`, where Maven picks it up. Point the consumer's
`dicechess.engine.version` at the `-SNAPSHOT` value while iterating, and remember to move it back
to a real release before opening a PR — CI has no access to your local repository, so a
`-SNAPSHOT` dependency that builds locally fails there.

---

## How Publishing Works

- `build.sbt` defines `publishTo` (GitHub Packages) and reads credentials from the
  `GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables; the `benchmark` module is excluded
  via `publish / skip := true`.
- Both CD workflows (`release.yaml` and `publish.yaml`) run
  `sbt "set ThisBuild / version := \"<tag>\"" rootJVM/publish`, so the registry always
  receives the clean release version without the `-SNAPSHOT` suffix.
- The steps are intentionally duplicated in both workflows: tags pushed by `release.yaml`
  via `GITHUB_TOKEN` do not trigger `publish.yaml` (GitHub's recursion guard).

See [CI/CD & Automated Releases](/dicechess-engine-scala/architecture/releases/) for the full pipeline.
