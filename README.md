# Dice Chess Engine (Scala) 🎲♟️

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=rabestro_dicechess-engine-scala&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=rabestro_dicechess-engine-scala)
[![CI Pipeline](https://github.com/rabestro/dicechess-engine-scala/actions/workflows/ci.yaml/badge.svg)](https://github.com/rabestro/dicechess-engine-scala/actions/workflows/ci.yaml)
[![Play Live](https://img.shields.io/badge/Play-Live-success)](https://play.jc.id.lv/)
[![Architecture Docs](https://img.shields.io/badge/Docs-Architecture-orange)](https://jc.id.lv/dicechess-engine-scala/)
[![Scaladoc API](https://img.shields.io/badge/Scaladoc-API-blue)](https://jc.id.lv/dicechess-engine-scala/api/)


An open-source, high-performance game engine and probability calculator for **Dice Chess**, built with **Scala 3**.

This engine is designed to calculate win probabilities, evaluate board positions, and suggest the best sequences of micro-moves based on stochastic search algorithms (Expectiminimax or Monte Carlo Tree Search).

---

## 📖 Dice Chess Rules & Turn Structure

Dice Chess is a stochastic variant of chess where players must roll a 6-sided die before making their moves. 

### Core Concepts:
1. **The Turn Structure**:
   * A player's turn consists of **1 Dice Roll** and **up to 3 micro-moves**.
   * The active color in the FEN **does not change** within the turn (i.e., during the 1st, 2nd, or 3rd micro-moves). It only changes when the turn ends (after 3 micro-moves, or when no legal moves are available / the player decides to pass).
   
2. **The Dice Roll**:
   * The die determines which pieces are allowed to move:
     * `1` = Pawn (♙)
     * `2` = Knight (♘)
     * `3` = Bishop (♗)
     * `4` = Rook (♖)
     * `5` = Queen (♕)
     * `6` = King (♔)
     
3. **Micro-moves**:
   * You can move **different pieces** or the **same piece** multiple times during your turn, as long as each piece's type matches the rolled die.
   * If you roll a `3` (Bishop), you can make up to 3 separate bishop moves.
   * **Victory Condition**: The game is won by **capturing the opponent's king** directly (there is no traditional check/mate, as the king can be captured on the next micro-move).
   * **Maximum Micro-moves**: Players must play moves that maximize the total number of micro-moves played in their turn. See our detailed [Maximum Micro-moves Rule Algorithm](docs/src/content/docs/architecture/move-generation/05-maximum-micromoves.md) guide for the mathematical formulation and pseudocode.

---

## 🛠️ Tech Stack & Architecture

This project is built using a **monorepo / cross-project** structure enabling seamless code reuse across platforms:

* **Language**: Scala 3 (leveraging modern opaque types, enums, and zero-cost abstractions)
* **Cross-Compilation**: `sbt-crossproject` compiling to:
  * **JS Platform (Scala.js)**: Produces an optimized, fast ES Module running directly in the [Svelte PWA Frontend](https://play.jc.id.lv/).
  * **JVM Platform**: For high-speed simulations, deep search tree experiments, and performance profiling.
* **JSON Serialization**: Circe (cross-platform)
* **Benchmarking**: Java Microbenchmark Harness (`sbt-jmh`)
* **Testing**: `MUnit` + `ScalaCheck` (property-based testing)

---

## 🔌 Core JavaScript API (`DiceChess` JS Module)

The Scala.js module exports a global/module object named `DiceChess` for seamless integration with the web platform:

### 1. Get Best Move for Bot
```javascript
// Request the best move sequence for a specific bot algorithm
const result = DiceChess.getBestMove(
  "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1 P", 
  { algorithm: "expectimax" }  // Options: random, checkmate-aware, greedy, cautious-greedy, aggressive, monte-carlo, expectimax
);

console.log(result);
/* Output:
{
  moves: [ 
    { from: "e2", to: "e4", promotion: null } 
  ],
  score: 45,
  timeTakenMs: 12
}
*/
```

### 2. Check Available Bots
```javascript
const bots = DiceChess.getAvailableBots();
// Returns metadata for: [Random, Checkmate-Aware, Greedy, Cautious Greedy, Aggressive, Monte-Carlo, Expectimax]
```

### 3. Utility API Functions
* `getLegalUciMoves(dfen)`: Returns legal moves in standard UCI format.
* `applyMove(dfen, from, to, promotion)`: Applies a micro-move and returns the new DFEN.
* `endTurn(dfen)`: Advances the game state to the next player's turn (color toggle, move counts, clears dice pool).

### 4. Doubling Cube & Draw Functions
* `shouldBotOfferDouble(dfen, currentStake, options)`: Evaluates whether the bot should offer a double.
* `shouldBotAcceptDouble(dfen, newStake, options)`: Evaluates whether the bot should accept a double.
* `shouldBotOfferDraw(dfen, options)`: Evaluates whether the bot should offer a draw.
* `shouldBotAcceptDraw(dfen, options)`: Evaluates whether the bot should accept a draw.

---

## 📦 Using the Engine as a JVM Library (Maven)

Every release publishes **three artifacts**:
* JVM artifact `lv.id.jc:dicechess-engine-scala_3` to the [GitHub Packages Maven registry](https://github.com/rabestro/dicechess-engine-scala/packages) (for JVM backends like `dicechess-analytics`)
* NPM package `@rabestro/dicechess-engine` (Scala.js, optimized ES Module for browsers)
* NPM package `@rabestro/dicechess-engine-wasm` (WebAssembly, for heavy computation in Web Workers)

The JVM artifact is the integration path for backends that need the engine as the source of truth for rules validation.

Scala consumers use the engine API directly. **Java and Kotlin consumers** (e.g. `dicechess-bot-java`)
bind to `dicechess.engine.jvmapi.JvmApi` instead — a narrow facade that keeps `Either` returns,
extension methods, and erased opaque types from reaching the caller. See the
[JVM API Reference](https://jc.id.lv/dicechess-engine-scala/architecture/jvm-api/).

GitHub Packages requires authentication even for public packages, so consumers need a token
with `read:packages` scope (locally: `GITHUB_ACTOR` + `GITHUB_TOKEN` environment variables):

```scala
resolvers += "GitHub Packages (dicechess-engine)" at
  "https://maven.pkg.github.com/rabestro/dicechess-engine-scala"

credentials ++= (for {
  user  <- sys.env.get("GITHUB_ACTOR")
  token <- sys.env.get("GITHUB_TOKEN")
} yield Credentials("GitHub Package Registry", "maven.pkg.github.com", user, token)).toSeq

libraryDependencies += "lv.id.jc" %% "dicechess-engine-scala" % "<latest release>"
```

For local development against unreleased changes, publish to the local Ivy repository:

```bash
mise run publish:local
```

---

## 🗺️ Roadmap & Milestones

See the full roadmap in [Architecture & Developer Guide](https://jc.id.lv/dicechess-engine-scala/architecture/milestones/).

```
[✅ v0.1 - Foundation & Core Types]
  ├── Project setup (SBT 2.x / Scala 3), mise configuration
  ├── Opaque types: Bitboard, Square, Piece, Color
  └── High-performance FEN (DFEN) Parser

[✅ v0.2 - Move Generation (Classic)]
  ├── Bitwise operations and precomputed attack tables (Magic Bitboards)
  ├── Pawn, knight, king, and sliding piece move generation
  └── Perft framework integration

[✅ v0.3 - Dice Chess Mechanics]
  ├── Dice roll representations and filtering
  ├── Game state management with random events
  └── Turn lifecycle: roll → micro-moves → endTurn

[✅ v0.4 - Basic Bot & Gameplay]
  ├── 7 Bot behaviors: Random, Checkmate-Aware, Greedy, Cautious Greedy, Aggressive, Monte-Carlo, Expectimax
  ├── Scala.js integration for browser execution
  └── Svelte/Vite PWA test harness

[✅ v0.5 - Evaluation & Heuristics]
  ├── Static evaluation (Material balance, Piece-Square Tables)
  ├── Zobrist Hashing and Transposition Tables
  └── King Capture Probability (216 dice outcomes)

[🚀 v0.6 - Expectimax Search Engine]
  ├── Deep Expectimax search with chance nodes (216 ordered rolls / 56 unique multisets)
  ├── Star1/Star2 pruning for chance nodes
  ├── Time management and budgeted search
  └── Rao-Blackwellized Monte-Carlo pre-roll equity estimator

[🔌 v0.7 - Advanced Features]
  ├── ONNX model integration for learned evaluation
  ├── Opening book support
  └── Draw and doubling cube logic

[🚀 v1.0 - Production & Optimization]
  ├── GraalVM Native Image compilation
  └── Performance optimizations and CI/CD improvements
```

---

## 📚 Documentation & API Reference

* **[Live Web App](https://play.jc.id.lv/)**: Play Dice Chess against our engine's active search algorithms in a premium Svelte PWA environment.
* **[Architecture & Developer Guide](https://jc.id.lv/dicechess-engine-scala/)**: Read about our Zero-Cost Abstractions, Hybrid Mailbox, and Milestone roadmap.
* **[Scaladoc API Reference](https://jc.id.lv/dicechess-engine-scala/api/)**: Comprehensive technical API documentation automatically generated from our codebase.


---

## 🚀 Getting Started

Ensure you have [mise](https://mise.jdx.dev/) installed for orchestrating the developer environment.

```bash
# Clone the repository
git clone https://github.com/rabestro/dicechess-engine-scala.git
cd dicechess-engine-scala

# Install the mise-managed toolchain (JDK, Node, scalafmt, lefthook, …)
mise install

# Install sbt and register git hooks (brew install sbt; lefthook install)
mise run setup

# Run the test suite and formatter checks
mise run check

# Open the interactive Scala 3 REPL loaded with the project
mise run console
```

---

## ⚖️ License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. 
