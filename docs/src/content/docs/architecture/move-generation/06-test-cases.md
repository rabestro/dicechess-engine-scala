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

### 1. Initial position: pawn moves

<div style="display: flex; flex-direction: row; gap: 24px; align-items: start; margin-bottom: 30px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;">
    <p style="margin-top: 0; margin-bottom: 16px;">Standard starting position. With a Pawn (1) rolled, any white pawn can advance one or two squares forward.</p>
    <ul style="list-style-type: disc; padding-left: 20px; margin-bottom: 0;">
      <li style="margin-bottom: 8px;"><strong>FEN:</strong> <code>rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1</code></li>
      <li style="margin-bottom: 8px;"><strong>Dice Rolled:</strong> ⚀ Pawn (1)</li>
      <li style="margin-bottom: 0;"><strong>Expected Legal Moves:</strong> <code>a2a3</code>, <code>a2a4</code>, <code>b2b3</code>, <code>b2b4</code>, <code>c2c3</code>, <code>c2c4</code>, <code>d2d3</code>, <code>d2d4</code>, <code>e2e3</code>, <code>e2e4</code>, <code>f2f3</code>, <code>f2f4</code>, <code>g2g3</code>, <code>g2g4</code>, <code>h2h3</code>, <code>h2h4</code></li>
    </ul>
  </div>
  <div style="flex: 0 0 280px; width: 280px; min-width: 280px; margin: 0 auto;">
    <img src="https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2Fpppppppp%2F8%2F8%2F8%2F8%2FPPPPPPPP%2FRNBQKBNR_w_KQkq_-_0_1&color=white&theme=brown&piece=cburnett" alt="Board Position" style="width: 100%; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.15); border: 1px solid rgba(0,0,0,0.08);" />
  </div>
</div>

---

### 2. King only under Rook roll

<div style="display: flex; flex-direction: row; gap: 24px; align-items: start; margin-bottom: 30px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;">
    <p style="margin-top: 0; margin-bottom: 16px;">The active player only has a King on the board. With a Rook (4) rolled, there are no rooks to move, and the King cannot act as a Rook. No legal moves are generated.</p>
    <ul style="list-style-type: disc; padding-left: 20px; margin-bottom: 0;">
      <li style="margin-bottom: 8px;"><strong>FEN:</strong> <code>4k3/8/8/8/8/8/8/4K3 w - - 0 1</code></li>
      <li style="margin-bottom: 8px;"><strong>Dice Rolled:</strong> ⚃ Rook (4)</li>
      <li style="margin-bottom: 0;"><strong>Expected Legal Moves:</strong> <em>None (no legal moves)</em></li>
    </ul>
  </div>
  <div style="flex: 0 0 280px; width: 280px; min-width: 280px; margin: 0 auto;">
    <img src="https://lichess1.org/export/fen.gif?fen=4k3%2F8%2F8%2F8%2F8%2F8%2F8%2F4K3_w_-_-_0_1&color=white&theme=brown&piece=cburnett" alt="Board Position" style="width: 100%; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.15); border: 1px solid rgba(0,0,0,0.08);" />
  </div>
</div>

---

### 3. King on the first row

<div style="display: flex; flex-direction: row; gap: 24px; align-items: start; margin-bottom: 30px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;">
    <p style="margin-top: 0; margin-bottom: 16px;">The King is located on e1 with no other pieces on the board. Under a King (6) roll, all standard adjacent moves are legal.</p>
    <ul style="list-style-type: disc; padding-left: 20px; margin-bottom: 0;">
      <li style="margin-bottom: 8px;"><strong>FEN:</strong> <code>4k3/8/8/8/8/8/8/4K3 w - - 0 1</code></li>
      <li style="margin-bottom: 8px;"><strong>Dice Rolled:</strong> ⚅ King (6)</li>
      <li style="margin-bottom: 0;"><strong>Expected Legal Moves:</strong> <code>e1d1</code>, <code>e1d2</code>, <code>e1e2</code>, <code>e1f2</code>, <code>e1f1</code></li>
    </ul>
  </div>
  <div style="flex: 0 0 280px; width: 280px; min-width: 280px; margin: 0 auto;">
    <img src="https://lichess1.org/export/fen.gif?fen=4k3%2F8%2F8%2F8%2F8%2F8%2F8%2F4K3_w_-_-_0_1&color=white&theme=brown&piece=cburnett" alt="Board Position" style="width: 100%; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.15); border: 1px solid rgba(0,0,0,0.08);" />
  </div>
</div>

---

### 4. Queen moves from f3

<div style="display: flex; flex-direction: row; gap: 24px; align-items: start; margin-bottom: 30px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;">
    <p style="margin-top: 0; margin-bottom: 16px;">The Queen is on f3 in a semi-open board. Under a Queen (5) roll, it can move along any unobstructed diagonal, rank, or file.</p>
    <ul style="list-style-type: disc; padding-left: 20px; margin-bottom: 0;">
      <li style="margin-bottom: 8px;"><strong>FEN:</strong> <code>rnbqkbnr/pppppppp/8/8/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 1</code></li>
      <li style="margin-bottom: 8px;"><strong>Dice Rolled:</strong> ⚄ Queen (5)</li>
      <li style="margin-bottom: 0;"><strong>Expected Legal Moves:</strong> <code>f3d1</code>, <code>f3e2</code>, <code>f3g4</code>, <code>f3h5</code>, <code>f3a3</code>, <code>f3b3</code>, <code>f3c3</code>, <code>f3d3</code>, <code>f3e3</code>, <code>f3g3</code>, <code>f3h3</code>, <code>f3f4</code>, <code>f3f5</code>, <code>f3f6</code>, <code>f3f7</code></li>
    </ul>
  </div>
  <div style="flex: 0 0 280px; width: 280px; min-width: 280px; margin: 0 auto;">
    <img src="https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2Fpppppppp%2F8%2F8%2F2B1P3%2F5Q2%2FPPPP1PPP%2FRNB1K1NR_w_KQkq_-_0_1&color=white&theme=brown&piece=cburnett" alt="Board Position" style="width: 100%; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.15); border: 1px solid rgba(0,0,0,0.08);" />
  </div>
</div>

---

### 5. En Passant and Path Blockage

<div style="display: flex; flex-direction: row; gap: 24px; align-items: start; margin-bottom: 30px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;">
    <p style="margin-top: 0; margin-bottom: 16px;">A complex pawn scenario: the pawn on e5 can capture the black d5 pawn en passant (exd6). The c2 pawn's two-square advance is blocked by the bishop on c4, so only c2-c3 is legal. The f2 pawn is completely blocked by the queen on f3.</p>
    <ul style="list-style-type: disc; padding-left: 20px; margin-bottom: 0;">
      <li style="margin-bottom: 8px;"><strong>FEN:</strong> <code>rnbqkbnr/ppp1pppp/8/3pP3/2B5/5Q2/PPPP1PPP/RNB1K1NR w KQkq d6 0 1</code></li>
      <li style="margin-bottom: 8px;"><strong>Dice Rolled:</strong> ⚀ Pawn (1)</li>
      <li style="margin-bottom: 0;"><strong>Expected Legal Moves:</strong> <code>a2a3</code>, <code>a2a4</code>, <code>b2b3</code>, <code>b2b4</code>, <code>c2c3</code>, <code>d2d3</code>, <code>d2d4</code>, <code>e5e6</code>, <code>e5d6</code>, <code>g2g3</code>, <code>g2g4</code>, <code>h2h3</code>, <code>h2h4</code></li>
    </ul>
  </div>
  <div style="flex: 0 0 280px; width: 280px; min-width: 280px; margin: 0 auto;">
    <img src="https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2Fppp1pppp%2F8%2F3pP3%2F2B5%2F5Q2%2FPPPP1PPP%2FRNB1K1NR_w_KQkq_d6_0_1&color=white&theme=brown&piece=cburnett" alt="Board Position" style="width: 100%; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.15); border: 1px solid rgba(0,0,0,0.08);" />
  </div>
</div>

---

## 2-Dice Scenarios

Move generator tests with two dice rolled. These evaluate intermediate micro-move sequences.

### 1. Initial position: pawn and knight moves

<div style="display: flex; flex-direction: row; gap: 24px; align-items: start; margin-bottom: 30px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;">
    <p style="margin-top: 0; margin-bottom: 16px;">Starting position with two dice rolled: Pawn (1) and Knight (2). Since no micro-move can block another in the initial turn, the legal moves are the sum of all individual legal pawn moves and all individual legal knight moves.</p>
    <ul style="list-style-type: disc; padding-left: 20px; margin-bottom: 0;">
      <li style="margin-bottom: 8px;"><strong>FEN:</strong> <code>rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1</code></li>
      <li style="margin-bottom: 8px;"><strong>Dice Rolled:</strong> ⚀ Pawn (1), ⚁ Knight (2)</li>
      <li style="margin-bottom: 0;"><strong>Expected Legal Moves:</strong> <code>a2a3</code>, <code>a2a4</code>, <code>b2b3</code>, <code>b2b4</code>, <code>c2c3</code>, <code>c2c4</code>, <code>d2d3</code>, <code>d2d4</code>, <code>e2e3</code>, <code>e2e4</code>, <code>f2f3</code>, <code>f2f4</code>, <code>g2g3</code>, <code>g2g4</code>, <code>h2h3</code>, <code>h2h4</code>, <code>b1a3</code>, <code>b1c3</code>, <code>g1f3</code>, <code>g1h3</code></li>
    </ul>
  </div>
  <div style="flex: 0 0 280px; width: 280px; min-width: 280px; margin: 0 auto;">
    <img src="https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2Fpppppppp%2F8%2F8%2F8%2F8%2FPPPPPPPP%2FRNBQKBNR_w_KQkq_-_0_1&color=white&theme=brown&piece=cburnett" alt="Board Position" style="width: 100%; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.15); border: 1px solid rgba(0,0,0,0.08);" />
  </div>
</div>

---

## 3-Dice Scenarios

Move generator tests with all three dice rolled. These verify full turn execution and complete path optimization.

### 1. Initial position: path optimization filter

<div style="display: flex; flex-direction: row; gap: 24px; align-items: start; margin-bottom: 30px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;">
    <p style="margin-top: 0; margin-bottom: 16px;">Starting position with all three dice rolled: Pawn (1), Knight (2), and Bishop (3). According to the Dice Chess maximum micro-moves rules, quiet a/c/f/h pawn moves are completely filtered out because they do not form or enable a valid 3-move sequence (e.g. Pawn -> Bishop -> Knight).</p>
    <ul style="list-style-type: disc; padding-left: 20px; margin-bottom: 0;">
      <li style="margin-bottom: 8px;"><strong>FEN:</strong> <code>rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1</code></li>
      <li style="margin-bottom: 8px;"><strong>Dice Rolled:</strong> ⚀ Pawn (1), ⚁ Knight (2), ⚂ Bishop (3)</li>
      <li style="margin-bottom: 0;"><strong>Expected Legal Moves:</strong> <code>b2b3</code>, <code>b2b4</code>, <code>d2d3</code>, <code>d2d4</code>, <code>e2e3</code>, <code>e2e4</code>, <code>g2g3</code>, <code>g2g4</code>, <code>b1a3</code>, <code>b1c3</code>, <code>g1f3</code>, <code>g1h3</code></li>
    </ul>
  </div>
  <div style="flex: 0 0 280px; width: 280px; min-width: 280px; margin: 0 auto;">
    <img src="https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2Fpppppppp%2F8%2F8%2F8%2F8%2FPPPPPPPP%2FRNBQKBNR_w_KQkq_-_0_1&color=white&theme=brown&piece=cburnett" alt="Board Position" style="width: 100%; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.15); border: 1px solid rgba(0,0,0,0.08);" />
  </div>
</div>

---

