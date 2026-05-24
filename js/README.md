# @rabestro/dicechess-engine 🎲♟️

High-performance, cross-platform game engine and move generator for **Dice Chess**, compiled to pure Javascript (ES Modules) with full TypeScript type declarations.

Designed for maximum performance in web browsers, hybrid apps, and Node.js servers.

---

## Installation

Install the package via npm or your preferred package manager:

```bash
npm install @rabestro/dicechess-engine
```

---

## Quick Start (JavaScript / TypeScript)

The package is distributed as a standard ES Module (`type: "module"`).

```javascript
import { DiceChess } from '@rabestro/dicechess-engine';

// 1. Get all legal moves as a flat array of UCI strings for the rolled dice
const fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
const dice = [1, 2]; // 1 = Pawn, 2 = Knight
const legalMoves = DiceChess.getLegalUciMoves(fen, dice);

console.log("Legal moves in this turn:", legalMoves);
// e.g. ["e2e3", "e2e4", "b1c3", "b1a3", ...]

// 2. Apply a move to get the updated FEN
const nextFen = DiceChess.applyMove(fen, "e2", "e4");
console.log("Next FEN:", nextFen);

// 3. Compute the best sequence of micro-moves using the greedy bot search
const botResult = DiceChess.getBestMove(nextFen, [3, 4], { algorithm: "greedy" });
console.log("Bot moves:", botResult.moves);
// e.g. [ { from: "g1", to: "f3", promotion: null } ]
```

---

## Features

* **Complete Dice Chess Rules**: Implements turn sequencing, unblocking, promotion path calculations, and strict **Maximal Micro-moves Filtering**.
* **Type-Safe**: Shipped with comprehensive TypeScript definition files (`.d.ts`) generated directly from the Scala compiler.
* **Side-Effect Free**: Configured with `sideEffects: false` to allow advanced tree-shaking in modern bundlers like Vite, Webpack, and Rollup.
* **Blazing Fast**: Optimized bitwise operations compiled via the advanced Scala.js optimizing linker.

---

## API Reference & Documentation

* **[Interactive User & Developer Guide](https://jc.id.lv/dicechess-engine-scala/)**: Complete rules, architectural documentation, and live interactive visual catalogs.
* **[GitHub Repository](https://github.com/rabestro/dicechess-engine-scala)**: Source code, issue tracker, and contribution guidelines.

---

## License

This package is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
