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
import { FenParser, LegalMovesFilter } from '@rabestro/dicechess-engine';

// 1. Parse a position from FEN
const fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
const parseResult = FenParser.parse(fen);

if (parseResult.isLeft()) {
  console.error("Invalid FEN:", parseResult.value);
} else {
  const gameState = parseResult.value; // GameState object
  
  // 2. Roll the dice (e.g. 1 = Pawn, 2 = Knight)
  const dice = [1, 2];
  
  // 3. Filter legal micro-moves under Dice Chess path optimization rules
  const legalMoves = LegalMovesFilter.filterMaximalMoves(gameState, dice);
  
  // 4. Output the legal move sequences (e.g. ["e2e4", "g1f3", ...])
  console.log("Legal moves in this turn:", legalMoves.map(m => m.toNotation()));
}
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
