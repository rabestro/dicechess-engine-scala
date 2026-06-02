## 1. Goal
Implement an interactive CLI REPL "sandbox" for the Dice Chess engine using `JLine 3` and `Decline`, featuring `eval` (static evaluation & board printing) and `arena` (bot vs bot matches with custom initial positions).

## 2. Approach
We will replace the current placeholder `Main.scala` with a continuous interactive shell. `JLine 3` will provide terminal features like history, graceful `Ctrl+C` handling, and tab-completion. `Decline` will parse the string input into structured Scala case classes.

To support the requested features:
1. **Board Printer:** A utility to render `GameState` in ASCII (with options for Latin letters or Unicode pieces).
2. **Arena Customization:** The existing `BotMatchRunner` currently hardcodes the standard FEN. We will refactor it to accept a custom `startFen` parameter, allowing the `arena` command to run endgame studies.

## 3. File Changes
- **Modify** `build.sbt`: Add `decline` (cross-platform) and `jline` (JVM-only) dependencies.
- **Modify** `jvm/src/main/scala/dicechess/engine/bench/BotMatchRunner.scala`: Update `simulateGame` and `runMatch` to accept a `startFen: String` parameter, removing the hardcoded reliance on `StartFen`.
- **Create** `jvm/src/main/scala/dicechess/engine/cli/BoardPrinter.scala`: Implement `printBoard(state: GameState, useUnicode: Boolean): String`.
- **Create** `jvm/src/main/scala/dicechess/engine/cli/Commands.scala`: Define Decline commands `eval` (with `--unicode` flag) and `arena` (with `--games` and `--fen` flags).
- **Modify** `jvm/src/main/scala/dicechess/engine/Main.scala`: Replace the static print with the JLine `LineReader` while-loop that passes lines to the Decline parser.
- **Create** `docs/src/content/docs/architecture/cli-repl.md`: Write documentation with examples for the new CLI.

## 4. Implementation Steps
**Task 1: Dependencies & Core Utils**
1. Add `"com.monovore" %%% "decline" % "2.4.1"` and `"org.typelevel" %%% "cats-core" % "2.10.0"` to main `libraryDependencies` in `build.sbt`.
2. Add `"org.jline" % "jline" % "3.25.1"` to `.jvmSettings` in `build.sbt`.
3. Create `BoardPrinter.scala` mapping `PieceType` to ASCII/Unicode chars.

**Task 2: Arena Engine Refactoring**
1. Modify `BotMatchRunner.simulateGame` to accept `startFen: String`.
2. Modify `BotMatchRunner.runMatch` to accept and pass through `startFen`.
3. Fix internal tests in `BotMatchRunnerSpec` that rely on the old signature.

**Task 3: Command Parsers & REPL Loop**
1. Create `Commands.scala`. Define `eval` (runs `FenParser`, `BoardPrinter`, and `KingCaptureProbability`) and `arena` (calls `BotMatchRunner`).
2. Update `Main.scala` to initialize `TerminalBuilder`, `StringsCompleter`, and `LineReaderBuilder`.
3. Implement the REPL `while(running)` loop handling `UserInterruptException` and executing parsed `Commands`.

**Task 4: Documentation**
1. Write Astro-compatible markdown in `cli-repl.md`.

## 5. Acceptance Criteria
- `sbt rootJVM/run` launches an interactive prompt (`dicechess>`).
- Pressing `TAB` correctly autocompletes `eval`, `arena`, `help`, `exit`.
- `eval rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` successfully displays the board and evaluates King Capture Probability.
- `eval <FEN> --unicode` correctly prints Unicode chess pieces instead of Latin letters.
- `arena greedy random --games 5 --fen 4k3/8/8/8/8/8/8/4K3 b - -` correctly simulates 10 games from the custom endgame FEN and outputs the summary table.

## 6. Verification Steps
1. Run `sbt "project rootJVM" test` to ensure `BotMatchRunner` refactoring didn't break benchmarks or tests.
2. Run `mise run run` and manually test the `eval` command with a valid and invalid FEN.
3. Manually test the `arena` command with a custom FEN to ensure it doesn't crash.

## 7. Risks & Mitigations
- **Risk:** JLine 3 might misbehave on Windows terminals.
- **Mitigation:** Setting `TerminalBuilder.system(true)` usually handles OS nuances. Decline will be strictly used purely for parsing arguments from the string.
- **Risk:** `decline` relies on Cats, which might slightly increase compile time or binary size (via Scala.js).
- **Mitigation:** The impact is minimal, and Circe already brings in some functional dependencies. We will just use the core decline library.