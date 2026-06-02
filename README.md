# Dice Chess Engine (Scala) 🎲♟️

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=rabestro_dicechess-engine-scala&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=rabestro_dicechess-engine-scala)
[![CI Pipeline](https://github.com/rabestro/dicechess-engine-scala/actions/workflows/ci.yaml/badge.svg)](https://github.com/rabestro/dicechess-engine-scala/actions/workflows/ci.yaml)
[![Play Live](https://img.shields.io/badge/Play-Live-success)](https://dc.jc.id.lv/)
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
  * **JS Platform (Scala.js)**: Produces an optimized, fast ES Module running directly in the [Svelte PWA Frontend](https://dc.jc.id.lv/).
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
  { algorithm: "prudent" }
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
// Returns metadata for: [Random, Checkmate-Aware, Greedy, Cautious Greedy, Aggressive, Prudent]
```

### 3. Utility API Functions
* `getLegalUciMoves(dfen)`: Returns legal moves in standard UCI format.
* `applyMove(dfen, from, to, promotion)`: Applies a micro-move and returns the new DFEN.
* `endTurn(dfen)`: Advances the game state to the next player's turn (color toggle, move counts, clears dice pool).
* `shouldBotOfferDouble(dfen, currentStake, options)`: Evaluates whether the bot should double.

---

## 🗺️ Roadmap & Hackathon Milestones

```
[✅ Phase 1: Foundation & Core Types]
 ├── Bitboard, Square, Piece, Color opaque types
 └── High-performance FEN (DFEN) Parser

[✅ Phase 2: Move Generation & Validation]
 ├── Sliding pieces attack tables (Magic Bitboards)
 ├── Maximum Micro-moves Rule validation & Turn Generator
 └── Fast JS API export (Scala.js) for frontend integration

[✅ Phase 3: Immediate Heuristics & Local AI]
 ├── 5+ Bot behaviors: Random, Checkmate-Aware, Greedy, Aggressive, Prudent
 └── Probabilistic King Capture analysis (calculating EV over 3d6 combinations)

[🚀 Phase 4: Hackathon Goals & Advanced AI]
 ├── Fast, deep Stochastic Search Engine (Expectimax with Alpha-Beta pruning)
 ├── Mass Bot-vs-Bot Self-Play simulations (JVM) for auto-tuning heuristic weights
 └── High-throughput evaluation benchmarks & low-level memory optimizations
```

---

## 📚 Documentation & API Reference

* **[Live Web App](https://dc.jc.id.lv/)**: Play Dice Chess against our engine's active search algorithms in a premium Svelte PWA environment.
* **[Architecture & Developer Guide](https://jc.id.lv/dicechess-engine-scala/)**: Read about our Zero-Cost Abstractions, Hybrid Mailbox, and Milestone roadmap.
* **[Scaladoc API Reference](https://jc.id.lv/dicechess-engine-scala/api/)**: Comprehensive technical API documentation automatically generated from our codebase.


---

## 🚀 Getting Started

Ensure you have [mise](https://mise.jdx.dev/) installed for orchestrating the developer environment.

```bash
# Clone the repository
git clone https://github.com/rabestro/dicechess-engine-scala.git
cd dicechess-engine-scala

# Installs all required tooling (JDK, Scala CLI, SBT, Formatting tools)
mise install

# Run the test suite and formatter checks
mise run check

# Open the interactive Scala 3 REPL loaded with the project
mise run console
```

---

## ⚖️ License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. 
