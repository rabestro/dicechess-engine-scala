# AGENTS.md

Branch naming rules and agent guidance

Allowed branch prefixes:
- `task` — work items / tasks
- `feat`  — new features
- `bug`   — bug fixes

Branch name pattern (required):
  (task|feat|bug)/<issue-number>-<short-description>
Example: `task/1234-fen-parser`

Agent rules (Copilot / automation):
- Do not implement or open a PR unless an issue exists and the branch is named according to the pattern.
- Agents may create draft changes, suggest code, and open the PR linked to the issue.
- Agents should run `mise run format` on any generated code and ensure `mise run check` passes successfully locally before proposing a PR.
- Human retains the ultimate authority to review, approve, and merge the PR.

## Developer Workflows

- **Core Runner**: Use `mise run <task>` from the root of the repository for all development tasks.
- **Code Formatting**: `mise run format` will run scalafmt across all sources.
- **Local CI validation**: `mise run check` automatically runs formatting checks, compiles everything, and executes the tests.
- **Interactive Shell**: `mise run console` spins up a Scala 3 REPL pre-configured with your project context.

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
    --label "core-types" \
    --label "ai-ready"

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
    --label "core-types" `
    --label "ai-ready"

  Remove-Item "temp_issue.md"
  ```

## Approved Milestones

Assign tasks to these milestones logically. Each milestone must be fully tested (including performance benchmarks) before moving to the next.
> [!IMPORTANT]
> You MUST strictly assign tasks ONLY to the following milestones. Do not create or invent new milestone names.

* **v0.1 - Foundation & Core Types**: Project setup (SBT 1.x / Scala 3), configuration, `mise` setup. Implementation of basic Opaque Types (`Bitboard`, `Square`, `Piece`, `Color`). Basic FEN parsing and serialization.
* **v0.2 - Move Generation (Classic)**: Bitwise operations, precomputed attack tables (Magic Bitboards). Pawn, knight, king, and sliding piece move generation. Perft (Performance Test) framework integration to verify move correctness.
* **v0.3 - Dice Chess Mechanics**: Dice roll representations, filtering pseudo-legal moves based on dice outcomes. Game state management with random events.
* **v0.4 - Evaluation & Heuristics**: Static evaluation function (Material balance, Piece-Square Tables). Zobrist Hashing and Transposition Tables (TT) for caching board states.
* **v0.5 - Expectimax Search Engine**: Core search algorithm implementation. Integration of Virtual Threads (`Ox`) for parallelizing probability branches. Mathematical expectation calculations.
* **v0.6 - WebSocket API**: Integration of `Http4s` (or `Cask`). Implementing the command protocol (`start_search`, `stop`, `info`, `bestmove`). Structured concurrency for search cancellation.
* **v1.0 - Production & Native Image**: GraalVM Native Image compilation, Dockerfile optimization, CI/CD pipelines, deployment configurations for Homelab / Oracle Cloud.

## Approved GitHub Labels

Use ONLY these labels when generating `gh` commands. Do not use any labels outside of this list.

* **Types**: 
  * `bug` — Code issues, logical defects, or runtime failures.
  * `enhancement` — Functional improvements or new features.
  * `refactoring` — Design restructuring without behavioral changes.
  * `documentation` — Inline docstrings, guides, or AGENTS.md updates.
  * `testing` — Adding unit, property-based, or integration tests.
  * `performance` — Micro-optimizations and engine search speedups.
  * `architecture` — Global module design or tech stack switches.
  * `ci-cd` — GitHub Actions, build scripts, or mise configuration.

* **Domains**:
  * `core-types` — Board, Bitboards, Pieces, and FEN parser.
  * `move-gen` — Move generator, Bitwise operations, attack tables.
  * `search` — Expectimax, Loom/Virtual Threads, pruning, Transposition Tables.
  * `evaluation` — Static board evaluators, Piece-Square tables.
  * `api` — HTTP, WebSockets, JSON serialization.
  * `infrastructure` — Docker, cloud hosting, VM configurations.

* **System**: 
  * `ai-ready` — Mandatory for well-scoped tasks. Acts as a strict contract that the Definition of Done is absolute and ready for an AI agent to implement.



## Documentation Standards (Scaladoc 3)

All engine code must follow these Scaladoc conventions:
* **Balanced Approach:** Do not document self-evident code (e.g., `def isWhite`). Document *Why*, not *What*.
* **Opaque Types:** Always document `opaque type` declarations, specifically describing their bitwise memory layout (e.g., Bit X-Y means Z) and companion objects.
* **Language:** All comments must be written strictly in **English**.
* **Formatting:**
  * Use standard triple-quote Scaladoc: `/** ... */`.
  * Leverage Markdown instead of HTML for lists and formatting.
  * Enclose code snippets and examples in `{{{ ... }}}`.
  * Use double brackets `[[Type]]` to reference other classes/objects.

## Testing Guidelines (Scala Engine)

* **Unit Testing**: Powered by `MUnit`. This is modern, incredibly fast, and has native-level support for Scala 3 and rich assertion diffs.
* **Property-Based Testing**: Use `ScalaCheck` via `munit-scalacheck` integration. Crucial for engine logic (e.g., *Property: FEN parsed to Board and serialized back must equal original FEN*).
* **Performance Testing (Perft)**:
* Move generator must be validated against standard Perft results (counting all possible nodes to depth N).
* Use `sbt-jmh` (Java Microbenchmark Harness) for micro-optimizations of bitwise operations. Engine code must be fast, allocations (GC) must be minimized.

* **API Testing**: WebSocket endpoints must be tested using local client mocks bypassing the actual search engine delay.
* **Fixtures**: Store standard FEN positions and their expected Perft node counts in `src/test/resources/perft_suite.json`.
