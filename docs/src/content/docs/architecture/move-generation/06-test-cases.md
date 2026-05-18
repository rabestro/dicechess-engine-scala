---
title: Move Generator Test Cases
description: Visual catalog of expert-vetted test cases for the Dice Chess move generator.
sidebar:
  order: 6
---

Dice Chess has a large and complex state space due to multi-move sequences, dice rolls, and path-optimization rules. This page cataloging all our active test cases is generated dynamically to let developers and chess experts visually inspect, verify, and document each scenario.

:::tip[Interactive Catalog]
Each test case includes a graphical representation of the position (rendered dynamically), details of the rolled dice, a complete explanation of the mechanics, and the final list of expected legal moves.
:::

## 1-Die Scenarios

Move generator tests with a single die rolled. These represent the fundamental building blocks of legal move filtering.

### 1. Initial position: pawn moves (1 die)

* **FEN:** `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1`
* **Dice Rolled:** ⚀ Pawn (1)
* **Expected Legal Moves:** `a2a3`, `a2a4`, `b2b3`, `b2b4`, `c2c3`, `c2c4`, `d2d3`, `d2d4`, `e2e3`, `e2e4`, `f2f3`, `f2f4`, `g2g3`, `g2g4`, `h2h3`, `h2h4`

![Board Position](https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2Fpppppppp%2F8%2F8%2F8%2F8%2FPPPPPPPP%2FRNBQKBNR_w_KQkq_-_0_1&color=white&theme=brown&piece=cburnett)

---

### 2. King only under Rook roll: no rooks on board (1 die)

* **FEN:** `4k3/8/8/8/8/8/8/4K3 w - - 0 1`
* **Dice Rolled:** ⚃ Rook (4)
* **Expected Legal Moves:** *None (no legal moves)*

![Board Position](https://lichess1.org/export/fen.gif?fen=4k3%2F8%2F8%2F8%2F8%2F8%2F8%2F4K3_w_-_-_0_1&color=white&theme=brown&piece=cburnett)

---

### 3. King on the first row and no other pieces (1 die)

* **FEN:** `4k3/8/8/8/8/8/8/4K3 w - - 0 1`
* **Dice Rolled:** ⚅ King (6)
* **Expected Legal Moves:** `e1d1`, `e1d2`, `e1e2`, `e1f2`, `e1f1`

![Board Position](https://lichess1.org/export/fen.gif?fen=4k3%2F8%2F8%2F8%2F8%2F8%2F8%2F4K3_w_-_-_0_1&color=white&theme=brown&piece=cburnett)

---

### 4. Queen moves from f3

* **FEN:** `rnbqkbnr/pppppppp/8/8/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 1`
* **Dice Rolled:** ⚄ Queen (5)
* **Expected Legal Moves:** `f3d1`, `f3e2`, `f3g4`, `f3h5`, `f3a3`, `f3b3`, `f3c3`, `f3d3`, `f3e3`, `f3g3`, `f3h3`, `f3f4`, `f3f5`, `f3f6`, `f3f7`

![Board Position](https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2Fpppppppp%2F8%2F8%2F2B1P3%2F5Q2%2FPPPP1PPP%2FRNB1K1NR_w_KQkq_-_0_1&color=white&theme=brown&piece=cburnett)

---

### 5. Pawn on e5 can capture en passant (exd6). Pawn on c2 can only advance one square (c2-c3) because c2-c4 is blocked by the bishop on c4. Pawn on f2 is blocked by the queen on f3.

* **FEN:** `rnbqkbnr/ppp1pppp/8/3pP3/2B5/5Q2/PPPP1PPP/RNB1K1NR w KQkq d6 0 1`
* **Dice Rolled:** ⚀ Pawn (1)
* **Expected Legal Moves:** `a2a3`, `a2a4`, `b2b3`, `b2b4`, `c2c3`, `d2d3`, `d2d4`, `e5e6`, `e5d6`, `g2g3`, `g2g4`, `h2h3`, `h2h4`

![Board Position](https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2Fppp1pppp%2F8%2F3pP3%2F2B5%2F5Q2%2FPPPP1PPP%2FRNB1K1NR_w_KQkq_d6_0_1&color=white&theme=brown&piece=cburnett)

---

## 2-Dice Scenarios

Move generator tests with two dice rolled. These evaluate intermediate micro-move sequences.

### 1. Initial position: pawn + knight moves (2 dice)

* **FEN:** `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1`
* **Dice Rolled:** ⚀ Pawn (1), ⚁ Knight (2)
* **Expected Legal Moves:** `a2a3`, `a2a4`, `b2b3`, `b2b4`, `c2c3`, `c2c4`, `d2d3`, `d2d4`, `e2e3`, `e2e4`, `f2f3`, `f2f4`, `g2g3`, `g2g4`, `h2h3`, `h2h4`, `b1a3`, `b1c3`, `g1f3`, `g1h3`

![Board Position](https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2Fpppppppp%2F8%2F8%2F8%2F8%2FPPPPPPPP%2FRNBQKBNR_w_KQkq_-_0_1&color=white&theme=brown&piece=cburnett)

---

## 3-Dice Scenarios

Move generator tests with all three dice rolled. These verify full turn execution and complete path optimization.

### 1. Initial position: pawn + knight + bishop moves. Quiet a/h/c/f pawn moves are filtered out because they do not enable any 3-move path (Pawn -> Bishop -> Knight).

* **FEN:** `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1`
* **Dice Rolled:** ⚀ Pawn (1), ⚁ Knight (2), ⚂ Bishop (3)
* **Expected Legal Moves:** `b2b3`, `b2b4`, `d2d3`, `d2d4`, `e2e3`, `e2e4`, `g2g3`, `g2g4`, `b1a3`, `b1c3`, `g1f3`, `g1h3`

![Board Position](https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2Fpppppppp%2F8%2F8%2F8%2F8%2FPPPPPPPP%2FRNBQKBNR_w_KQkq_-_0_1&color=white&theme=brown&piece=cburnett)

---

