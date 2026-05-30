---
title: Single-Turn Bot Strategies (Levels 1-5)
description: Detailed guide to the five single-turn bot search algorithms implemented in the Dice Chess engine.
sidebar:
  order: 6
---

The engine's initial bot layer consists of five **single-turn (primitive) search algorithms**. Unlike deep tree-search algorithms (like the upcoming Expectimax), these bots do not search the opponent's reply tree. Instead, they solve a narrower tactical optimization problem:

1. Enumerate every legal full-turn path for the rolled dice using `TurnGenerator.generateAllLegalTurnPaths`.
2. Score each resulting position from the active player's perspective.
3. Select either a random legal path or the optimal path according to a specialized heuristic.

This page documents the exact behavior and equations for all 5 bots implemented in `shared/src/main/scala/dicechess/engine/search/`.

---

## The Shared Evaluation Pipeline

All single-turn bots share the same core execution pipeline:

```mermaid
graph TD
    A["Input: GameState + dice list"] --> B["TurnGenerator.generateAllLegalTurnPaths"]
    B --> C{"Any legal paths?"}
    C -- No --> D["Return None (Pass)"]
    C -- Yes --> E["Evaluate candidates via SearchScoring.scorePath"]
    E --> F["Apply turn path to get final board state"]
    F --> G["Heuristic Evaluation function (evalFn)"]
    G --> H{"Selection Strategy"}
    H -- Random --> I["Choose a path uniformly at random"]
    H -- Strategic --> J["Pick optimal path (tie-break by shortest winning line)"]
    I --> K["Return ScoredSequence"]
    J --> K
```

A critical property of Dice Chess is that these bots evaluate **full turn paths** (consisting of 1 to 3 micro-moves) as a single cohesive action, rather than evaluating individual micro-moves in isolation.

---

## Detailed Roster of Bot Strategies

### Level 1: Random Bot (`RandomSearch`)
* **Difficulty:** 1
* **Philosophy:** Completely blind to board state, representing pure chance.
* **Selection Logic:** Uniformly selects any legal turn path at random.
* **Purpose:** Acts as a baseline correctness check for move generation and a control group for benchmarking smarter bots.

---

### Level 2: Checkmate-Aware Bot (`CheckmateAwareSearch`)
* **Difficulty:** 2
* **Philosophy:** The first spark of tactical awareness. Cares *only* about the King (winning the game or surviving) and is completely blind to material values.
* **Selection Logic:**
  1. **Immediate Win:** Looks for any turn path that captures the opponent's King (`TerminalWinScore`). If one exists, it is selected immediately.
  2. **King Safety:** If no win is available, it filters paths to those where its own King is **not attacked** at the end of the turn:
     $$
     \text{Evaluator.evaluateKingSafety}(\text{finalState}, \text{myColor}) = 0
     $$
     It then selects a safe path randomly.
  3. **Fallback:** If all moves leave the King under attack, it falls back to selecting any legal path randomly.
* **Performance Optimization:** To minimize CPU overhead, it passes a dummy evaluator `(_, _) => 0` to `scorePath`, bypassing all material-counting calculations.

---

### Level 3: Greedy Bot (`GreedySearch`)
* **Difficulty:** 3
* **Philosophy:** Highly aggressive but short-sighted materialist.
* **Selection Logic:**
  - Evaluates all paths using **material balance** (`Evaluator.evaluateMaterial`).
  - Chooses the path that maximizes friendly material minus opponent material.
  - **Tie-Breaker:** If multiple paths win the game immediately, it prefers the **shortest path** to deliver checkmate as quickly as possible. Remaining ties are broken randomly.
* **Material Weights:**
  - Pawn = `100` | Knight/Bishop = `300` | Rook = `500` | Queen = `900` | King = `10000`

---

### Level 4: Cautious Greedy Bot (`GreedySearchV2`)
* **Difficulty:** 4
* **Philosophy:** Balances greed with self-preservation to avoid walking into immediate checkmates.
* **Selection Logic:**
  - Evaluates paths using both **material balance** and **King safety** (`Evaluator.evaluate`).
  - If a path leaves its own King under attack at the end of the turn, it applies a heavy **King Exposure Penalty** of `-2000` centipawns (greater than two Queens).
  - Selects the path maximizing the combined score:
    $$
    \text{Score} = \text{MaterialScore} + \text{KingSafetyScore}
    $$

---

### Level 5: Aggressive Bot (`AggressiveSearch`)
* **Difficulty:** 5
* **Philosophy:** Relentlessly hunts the opponent's King, pushes pawns forward, and coordinates its pieces around the enemy King's ring.
* **Selection Logic:**
  - Evaluates paths using a highly specialized attacking evaluation function (`Evaluator.evaluateAggressive`) that aggregates four distinct heuristics:
    $$
    \text{AggressiveScore} = \text{StandardScore} + \text{PawnStorm} + \text{KingProximity} + \text{KingRingPressure}
    $$

#### Algorithmic Heuristics under the Hood

1. **Pawn Storm Heuristic:**
   Encourages pawns to advance forward aggressively to open files and create attacking targets.
   * White pawns: `(rank - 2) * 15` centipawns.
   * Black pawns: `(7 - rank) * 15` centipawns.

2. **King Proximity Heuristic:**
   Measures the Chebyshev distance of active friendly pieces (Knights, Bishops, Rooks, Queens) to the enemy King's square $(EK_{\text{rank}}, EK_{\text{file}})$, rewarding pieces that cluster closer to the target.
   * Chebyshev distance: $\text{dist} = \max(|sq_{\text{rank}} - EK_{\text{rank}}|, |sq_{\text{file}} - EK_{\text{file}}|)$
   * Formula: $\sum (8 - \text{dist}) \times \text{weight}_{\text{piece}}$
   * Weights: Knight/Bishop = `15`, Rook = `25`, Queen = `40`.

3. **King Ring Pressure Heuristic:**
   Awards points for controlling the 8 squares immediately surrounding the enemy King (the "King Ring").
   * For each adjacent square, if a friendly piece attacks it (using `MoveGenerator.isSquareAttacked`), it receives `25` centipawns per attacking piece.

---

## Comparison of O(1) Bot Strategies

| Aspect | Level 1: Random | Level 2: Checkmate Aware | Level 3: Greedy | Level 4: Cautious Greedy | Level 5: Aggressive |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Material Aware** | No | No | Yes (Heuristic) | Yes (Heuristic) | Yes (Heuristic) |
| **King Safety Aware** | No | Yes (Binary Filter) | No | Yes (Exposure Penalty) | Yes (Exposure Penalty) |
| **Attacking Focus** | None | Direct King Capture | Loose captures | Loose captures | King hunt, Pawn Storm, Proximity |
| **Evaluator Used** | None | Dummy `(_, _) => 0` | `evaluateMaterial` | `evaluate` | `evaluateAggressive` |
| **Play Style** | Chaotic | Defensive & Evasive | Aggressive & Reckless | Balanced & Safe | Threatening & Pressuring |

---

### Empirical Strength & Win Rates (Battle Arena Benchmark)

To objectively evaluate the playing strength of these O(1) algorithms, we run large-scale simulations in the JVM Battle Arena. The benchmark plays **18,000 games per match** (9,000 games as White and 9,000 games as Black to eliminate color bias), using the **Greedy Bot** as the baseline. 

A draw is enforced by the **50-move rule** (100 half-moves without captures or pawn pushes) to prevent infinite loops and games hanging.

The empirical results from a simulation of 90,000 games are as follows:

| Bot Strategy | Total Games | Wins (W/B) | Losses (W/B) | Draws (W/B) | Win Rate vs Greedy | Execution Time (18k games) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Level 1: Random** | 18,000 | 525 (246/279) | 17,475 (8754/8721) | 0 (0/0) | **2.9%** | ~52s |
| **Level 2: Checkmate Aware** | 18,000 | 7,787 (3855/3932) | 10,213 (5145/5068) | 0 (0/0) | **43.3%** | ~58s |
| **Level 3: Greedy (Baseline)** | 18,000 | 9,015 (4513/4502) | 8,985 (4487/4498) | 0 (0/0) | **50.1%** | **~41s** |
| **Level 4: Cautious Greedy** | 18,000 | 10,069 (5077/4992) | 7,931 (3923/4008) | 0 (0/0) | **55.9%** | ~46s |
| **Level 5: Aggressive** | 18,000 | 10,935 (5524/5411) | 7,065 (3476/3589) | 0 (0/0) | **60.8%** | ~49s |

#### Key Insights from the Benchmarks:
1. **Perfect Balance**: The `Greedy` vs `Greedy` baseline match yields a near-perfect **50.1%** win rate, confirming that color alternation and random tie-breaking operate without first-move bias.
2. **Material vs Checkmate**: Despite being blind to piece values, the **Checkmate Aware** bot scores a respectable **43.3%** against Greedy, demonstrating the high tactical value of King safety and direct threat detection in Dice Chess.
3. **Defense Pays Off**: **Cautious Greedy** (V2) outperforms standard Greedy by **5.8%**, proving that applying a penalty for exposing the King significantly increases win rates.
4. **Coordination Domination**: The **Aggressive** bot dominates the baseline with a **60.8%** win rate, showing that combining forward pawn pushes, piece proximity, and control of the enemy King Ring is highly effective.

---

## Computational Limitations of Single-Turn Search

While these bots perform incredibly well for instant tactical matches, they remain limited by their short horizon:
- **No Reply Tree:** They only evaluate the board at the end of *their* turn. They do not simulate the opponent's reply tree.
- **No Probability Weighting:** They do not calculate the mathematical expectations of the opponent's future dice rolls.
- **Eager Enumeration:** They generate and score all possible legal paths eagerly, which is cheap for 1-turn depths but unsustainable for deep searches.

These limits are addressed in **Expectimax Search (Stage 3 / Difficulty 6+)**, which introduces probabilistic tree search, virtual thread concurrency, and transposition caches.
