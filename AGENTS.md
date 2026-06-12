# AGENTS.md

Guidance for AI agents and contributors working on the Dice Chess engine.

## Project Context & Ecosystem Role

This engine is the **single source of truth for Dice Chess rules** across the whole ecosystem.
Complex game logic (move legality, the Maximum Micro-moves Rule, DFEN parsing/validation,
probability calculations) lives here, in Scala 3 — downstream projects must consume it rather
than re-implement it.

Published artifacts and their consumers:

| Artifact | Registry | Consumers |
|---|---|---|
| `@rabestro/dicechess-engine` (npm, Scala.js ES module) | GitHub Packages npm | `dicechess-analytics-ui`, web frontends |
| `lv.id.jc:dicechess-engine-scala_3` (JVM jar) | GitHub Packages Maven | `dicechess-analytics` (Scala 3 backend), ETL, bots |

Consequences for development:

- **Public API stability matters.** Breaking changes to the JS API (`js/src/.../api/JsApi.scala`),
  the DFEN format, or public JVM types require a deliberate decision and a minor/major version bump.
- The DFEN format is the shared contract:
  `<placement> <activeColor> <castling> <enPassant> <halfMove> <fullMove> [<dicePool>]`.
- Sibling repositories: `dicechess-analytics` (PostgreSQL + Scala 3 backend, the games database),
  `dicechess-analytics-ui` (SvelteKit frontend), `dicechess-bots`.
  `dicechess-lab` is frozen and serves only as an experiments archive.

## Architecture Map

Cross-compiled sbt project (JVM + Scala.js) — shared core, thin platform layers:

- `shared/src/main/scala/dicechess/engine/domain/` — immutable game state: opaque types
  (`Bitboard`, `Square`, `Piece`, `Color`), `Position`, `GameFlags`, `FenParser` (DFEN).
- `shared/.../movegen/` — bitboard move generation: `MagicBitboards` (sliding pieces),
  `LeaperAttacks`, `PawnGeneration`, `LegalMovesFilter` (Maximum Micro-moves Rule).
- `shared/.../search/` — `TurnGenerator` (exhaustive micro-move paths), `Evaluator`,
  `BotRegistry` (six strategies), `KingCaptureProbability` (216 dice outcomes).
- `jvm/` — JLine REPL CLI (`Main.scala`), bot arena (`bench/BotMatchRunner.scala`).
- `js/` — `api/JsApi.scala`: the `@JSExportTopLevel("DiceChess")` public JS API.
- `benchmark/` — JMH micro-benchmarks (excluded from coverage and publishing).

## Quality Gates (non-negotiable)

- `mise run check` must pass before any PR: scalafmt + scalafix checks, full test suite, coverage.
- The compiler is strict: `-Werror`, `-Wunused:all`, `-language:strictEquality`, `-Yexplicit-nulls`.
- Statement coverage minimum: **85%** (`coverageFailOnMinimum := true`).
- Hot paths (movegen, search) are allocation-sensitive: prefer opaque types, `inline def`,
  bitwise ops; validate performance-affecting changes with JMH (`mise run bench:quick`).
- SonarCloud scans run in CI; locally, agents can query issues and quality gates via the
  SonarQube MCP server configured in `.mcp.json` (requires `SONARQUBE_TOKEN` in the environment).

## Releases & Publishing

- Releases are cut via the manual **"Ops: Release"** workflow (`release.yaml`): version bump in
  `build.sbt` → git tag `vX.Y.Z` → npm package + Maven artifact + GitHub Release assets.
- `publish.yaml` handles manually pushed tags. Tags pushed by `release.yaml` via `GITHUB_TOKEN`
  do **not** trigger `publish.yaml` (GitHub recursion guard) — that is why both workflows
  contain the same publish steps.
- Both registries receive the clean version (no `-SNAPSHOT`); CI overrides the sbt version
  from the tag.
- Downstream development against unreleased changes: `mise run publish:local` publishes the
  JVM artifact to the local Ivy repository.

## Developer Workflows

- **Core Runner**: Use `mise run <task>` from the root of the repository for all development tasks.
- **Code Formatting**: `mise run format` will run scalafmt across all sources.
- **Local CI validation**: `mise run check` automatically runs formatting checks, compiles everything, and executes the tests.
- **Interactive Shell**: `mise run console` spins up a Scala 3 REPL pre-configured with your project context.
- **Static Analysis (SonarCloud)**: Use the SonarQube MCP server (configured in `.mcp.json`,
  Docker-based) to list issues and check quality gates for project
  `rabestro_dicechess-engine-scala`. Requires the `SONARQUBE_TOKEN` environment variable
  (generate at https://sonarcloud.io/account/security).

## Branch Naming & Agent Rules

Allowed branch prefixes:
- `task` — work items / tasks
- `feat` — new features
- `bug` — bug fixes

Branch name pattern (required):
  (task|feat|bug)/<issue-number>-<short-description>
Example: `task/1234-fen-parser`

Agent rules (Claude / Copilot / automation):
- Do not implement or open a PR unless an issue exists and the branch is named according to the pattern.
- Agents may create draft changes, suggest code, and open the PR linked to the issue.
- Agents should run `mise run format` on any generated code and ensure `mise run check` passes successfully locally before proposing a PR.
- Human retains the ultimate authority to review, approve, and merge the PR.

## Issue & Branch Management

- **Branch Naming**: Always create a dedicated branch for each task using the format `task/[ID]-[description]`, `feat/[ID]-[description]`, or `bug/[ID]-[description]`.
- **Workflow**:
  1. Create a GitHub Issue with Context, Objective, and DoD (Definition of Done).
  2. Create a branch and an Implementation Plan (if the task is complex).
  3. Execute, verify (`mise run check`), and create a Pull Request.
  4. Wait 1–3 minutes for automated comments or reviews on the PR, then read and address them.

- **Issue Creation Detail**: 
  1. Define Context, Objective, and Definition of Done (DoD)
  2. Choose appropriate **Milestone** and **Labels** (e.g., `core-types`, `move-gen`, `testing`)
  3. Write the body to a temporary file
  4. Execute `gh issue create --title "..." --body-file "temp_issue.md" --milestone "..." --label "..."`
  5. Remove the temporary file

- **Bash/Zsh Example**:
  ```bash
  cat << 'EOF' > temp_issue.md
  ## Context
  [Context of the task]

  ## Objective
  [Objective of the implementation]

  ## Definition of Done (DoD)
  - [ ] Unit tests passed
  - [ ] Core implementation complete
  - [ ] Performance impact validated (if applicable)
  EOF

  gh issue create \
    --title "Implement FEN Parser" \
    --body-file "temp_issue.md" \
    --milestone "v0.1 - Foundation & Core Types" \
    --label "enhancement" \
    --label "core-types"

  rm temp_issue.md
  ```

- **PowerShell Example**:
  ```powershell
  $issueBody = @'
  ## Context
  [Context of the task]

  ## Objective
  [Objective of the implementation]

  ## Definition of Done (DoD)
  - [ ] Unit tests passed
  - [ ] Core implementation complete
  - [ ] Performance impact validated (if applicable)
  '@

  $issueBody | Out-File -FilePath "temp_issue.md" -Encoding utf8

  gh issue create `
    --title "Implement FEN Parser" `
    --body-file "temp_issue.md" `
    --milestone "v0.1 - Foundation & Core Types" `
    --label "enhancement" `
    --label "core-types"

  Remove-Item "temp_issue.md"
  ```

## Approved Milestones

Assign tasks to these milestones logically. Each milestone must be fully tested (including performance benchmarks) before moving to the next.

[View current milestones on GitHub](https://github.com/rabestro/dicechess-engine-scala/milestones?sort=title&direction=asc)

> [!IMPORTANT]
> You MUST strictly assign tasks ONLY to the following milestones. Do not create or invent new milestone names.

* **v0.1 - Foundation & Core Types**: Project setup (SBT 1.x / Scala 3), configuration, `mise` setup. Implementation of basic Opaque Types (`Bitboard`, `Square`, `Piece`, `Color`). Basic FEN parsing and serialization. *(closed)*
* **v0.2 - Move Generation (Classic)**: Bitwise operations, precomputed attack tables (Magic Bitboards). Pawn, knight, king, and sliding piece move generation. Perft (Performance Test) framework integration to verify move correctness.
* **v0.3 - Dice Chess Mechanics**: Dice roll representations, filtering pseudo-legal moves based on dice outcomes. Game state management with random events.
* **v0.4 - Basic Bot & Gameplay**: Dedicated test harness (Svelte/Vite PWA). Implementation of a simple random or greedy bot using Scala.js to validate game state transitions and dice mechanics in a live browser environment.
* **v0.5 - Evaluation & Heuristics**: Static evaluation function (Material balance, Piece-Square Tables). Zobrist Hashing and Transposition Tables (TT) for caching board states.
* **v0.6 - Expectimax Search Engine**: Core search algorithm implementation. Integration of Virtual Threads (`Ox`) for parallelizing probability branches. Mathematical expectation calculations.
* **v0.7 - WebSocket API**: Integration of `Http4s` (or `Cask`). Implementing the command protocol (`start_search`, `stop`, `info`, `bestmove`). Structured concurrency for search cancellation.
* **v1.0 - Production & Native Image**: GraalVM Native Image compilation, Dockerfile optimization, CI/CD pipelines, deployment configurations for Homelab / Oracle Cloud.

## Approved GitHub Labels

Use ONLY these labels when generating `gh` commands. Do not use any labels outside of this list.

* **Shared core** (identical across all Dice Chess repositories):
  * `bug` — Code issues, logical defects, or runtime failures.
  * `enhancement` — Functional improvements or new features.
  * `refactoring` — Design restructuring without behavioral changes.
  * `documentation` — Inline docstrings, guides, or AGENTS.md updates.
  * `testing` — Adding unit, property-based, or integration tests.
  * `performance` — Micro-optimizations and engine search speedups.
  * `ci-cd` — GitHub Actions, build scripts, or mise configuration.
  * `dependencies` — Dependency updates (applied by dependabot).

* **Domains** (this repository only):
  * `core-types` — Board, Bitboards, Pieces, and FEN parser.
  * `move-gen` — Move generator, Bitwise operations, attack tables.
  * `search` — Expectimax, Loom/Virtual Threads, pruning, Transposition Tables.
  * `evaluation` — Static board evaluators, Piece-Square tables.
  * `api` — HTTP, WebSockets, JSON serialization.
  * `infrastructure` — Docker, cloud hosting, VM configurations.

## Documentation Standards (Scaladoc 3)

All engine code must follow these Scaladoc conventions:
* **Balanced Approach:** Do not document self-evident code (e.g., `def isWhite`). Document *Why*, not *What*.
* **Opaque Types:** Always document `opaque type` declarations, specifically describing their bitwise memory layout (e.g., Bit X-Y means Z) and companion objects.
* **Language:** All comments must be written strictly in **English**.
* **Formatting:**
  * Use standard triple-quote Scaladoc: `/** ... */`.
  * Leverage Markdown instead of HTML for lists and formatting.
  * Enclose code snippets and examples in standard Markdown code fences (e.g., ```scala). Do NOT use legacy `{{{ ... }}}` syntax.
  * Use double brackets `[[Type]]` to reference other classes/objects.

## Testing Guidelines (Scala Engine)

* **Unit Testing**: Powered by `MUnit`. This is modern, incredibly fast, and has native-level support for Scala 3 and rich assertion diffs.
* **Property-Based Testing**: Use `ScalaCheck` via `munit-scalacheck` integration. Crucial for engine logic (e.g., *Property: FEN parsed to Board and serialized back must equal original FEN*).
* **Performance Testing (Perft)**:
* Move generator must be validated against standard Perft results (counting all possible nodes to depth N).
* Use `sbt-jmh` (Java Microbenchmark Harness) for micro-optimizations of bitwise operations. Engine code must be fast, allocations (GC) must be minimized.

* **API Testing**: WebSocket endpoints must be tested using local client mocks bypassing the actual search engine delay.
* **Fixtures**: Store standard FEN positions and their expected Perft node counts in `src/test/resources/perft_suite.json`.
