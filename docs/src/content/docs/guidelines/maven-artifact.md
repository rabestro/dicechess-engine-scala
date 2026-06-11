---
title: Maven Artifact & JVM Integration
description: How the engine is published as a JVM library to the GitHub Packages Maven registry and how downstream Scala projects consume it.
---

The engine is the **single source of truth for Dice Chess rules** across the ecosystem. JVM
backends — first of all [dicechess-analytics](https://github.com/rabestro/dicechess-analytics)
(the Scala 3 analytics backend) — consume it as a regular Maven dependency instead of
re-implementing game logic.

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

## Local Development Against Unreleased Changes

When a downstream project needs engine changes that are not released yet, publish the JVM
artifact to the local Ivy repository:

```bash
mise run publish:local
```

This publishes the current `-SNAPSHOT` version to `~/.ivy2/local`, where sbt resolves it
before any remote registry.

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
