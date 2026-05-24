---
title: JavaScript API (DiceChess)
description: Reference documentation for the Dice Chess Engine JavaScript API.
---

The Dice Chess Engine exposes the `DiceChess` object to JavaScript consumers (like the `dicechess-lab` PWA frontend). This API provides functions for move generation, validation, and game state transitions.

## `DiceChess`

The primary interface for interacting with the engine from JavaScript/TypeScript.

### `getLegalMoves`

Returns all legal moves for a given position and a set of available dice rolls.

```typescript
function getLegalMoves(fen: string, dice: number[]): Record<string, string[]>
```

**Returns:** An object mapping origin squares to an array of valid destination squares. This format is directly compatible with the `dests` property of [Chessground](https://github.com/lichess-org/chessground).

---

### `applyMove`

Applies a move to the given FEN and returns the resulting board state. 
This function acts as the **Single Source of Truth** for chess rules, ensuring correct handling of castling rights, en passant, and pawn promotions.

```typescript
function applyMove(fen: string, from: string, to: string, promotion?: string): string | undefined
```

**Returns:** The updated FEN string after the move is applied, or `undefined` if the move is pseudo-illegal.

---

### `getBestMove`

Computes the best sequence of micro-moves for the given position and available dice using the engine's search algorithms.

```typescript
function getBestMove(fen: string, dice: number[], options?: { algorithm?: string }): {
    moves: { from: string, to: string, promotion: string | null }[],
    score: number,
    timeTakenMs: number
}
```

- `options.algorithm`: Can be `"greedy"` (default) or `"random"`.

---

### `isValidMove`

Validates if a move is legal for the given position and any of the available dice rolls.

```typescript
function isValidMove(fen: string, dice: number[], from: string, to: string): boolean
```

---

### `getPieceFromDice`

Returns the piece type notation associated with a dice roll.

```typescript
function getPieceFromDice(dice: number): string | null
```

* `1` → `"p"`
* `2` → `"n"`
* `3` → `"b"`
* `4` → `"r"`
* `5` → `"q"`
* `6` → `"k"`
