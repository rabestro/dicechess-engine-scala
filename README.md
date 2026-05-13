# Dice Chess Engine (Scala) 🎲♟️

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=rabestro_dicechess-engine-scala&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=rabestro_dicechess-engine-scala)
[![CI Pipeline](https://github.com/rabestro/dicechess-engine-scala/actions/workflows/ci.yml/badge.svg)](https://github.com/rabestro/dicechess-engine-scala/actions/workflows/ci.yml)

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

---

## 🛠️ Tech Stack

* **Language**: Scala 3 (leveraging modern enums, case classes, and type safety)
* **Concurrency**: Cats Effect 3 / ZIO (for high-performance parallel tree search)
* **API Framework**: Tapir / Http4s (exposing lightweight REST endpoints)
* **Build Tool**: sbt (Simple Build Tool)

---

## 🗺️ Pre-Hackathon & Hackathon Roadmap

```
[Phase 0: Pre-Hackathon - 2 Months Prep]
 ├── Setup repository structure & Domain Models (Scala 3)
 ├── Write a FEN Parser for Dice Chess
 └── Define & implement basic API endpoints (mock responses)

[Phase 1: Hackathon Day 1 - Morning]
 ├── Core Move Generator (handling the 3 micro-moves sequence)
 └── Basic Greedy/Random evaluation

[Phase 2: Hackathon Day 1 - Afternoon & Night]
 ├── Search Engine implementation (Expectimax or MCTS)
 └── Position Evaluation Function (Material, Position, Mobilty)

[Phase 3: Hackathon Day 2 - Morning]
 ├── Integration of HTTP API with the main PWA Client
 └── Performance profiling and bug fixing

[Phase 4: Hackathon Day 2 - Afternoon]
 └── Demo preparation, benchmarks, and final presentation!
```

---

## 🔌 API Contract (JSON)

### 1. Suggest Best Move
`POST /api/v1/engine/suggest`

#### Request
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "dice_roll": 1,
  "depth": 3
}
```

#### Response
```json
{
  "suggested_turn": {
    "dice_roll": 1,
    "micro_moves": [
      { "from": "e2", "to": "e4" },
      { "from": "d2", "to": "d4" }
    ]
  },
  "evaluation": {
    "win_probability": 0.545,
    "score_cp": 45
  },
  "nodes_searched": 1420500,
  "time_ms": 120
}
```

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
