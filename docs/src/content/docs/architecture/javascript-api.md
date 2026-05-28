---
title: JavaScript API (DiceChess)
description: Reference documentation for the Dice Chess Engine JavaScript API.
---

The Dice Chess Engine exposes the `DiceChess` object to JavaScript consumers (like the `dicechess-lab` PWA frontend). This API provides functions for move generation, validation, and game state transitions.

## `DiceChess`

The primary interface for interacting with the engine from JavaScript/TypeScript.

### `getLegalUciMoves`

Returns all legal moves for a given position and a set of available dice rolls as a flat array of UCI strings (e.g., `["e2e4", "e7e8q"]`).

```typescript
function getLegalUciMoves(fen: string, dice: number[]): string[]
```

**Returns:** An array of full UCI move strings. If a pawn promotion is legal, the 5th character contains the target piece notation (e.g., `"e7e8q"`).

---

### `applyMove`

Applies a **micro-move** to the given FEN and returns the resulting board state. 
This function acts as the **Single Source of Truth** for chess rules, ensuring correct handling of castling rights, en passant, and pawn promotions.

> [!NOTE]  
> Because a Dice Chess turn consists of multiple micro-moves, `applyMove` **does not** transition the turn to the opponent. The active color and full-move number remain unchanged. To formally end a turn, you must call `endTurn`.

```typescript
function applyMove(fen: string, from: string, to: string, promotion?: string): string | undefined
```

**Returns:** The updated FEN string after the move is applied, or `undefined` if the move is pseudo-illegal.

---

### `endTurn`

Explicitly ends the current player's turn. This function is critical for the micro-move architecture. It performs three vital operations:
1. Toggles the active color to the opponent.
2. Increments the full-move number (if the current player was Black).
3. Clears any stale *en-passant* targets from the previous turn, preventing illegal captures.

```typescript
function endTurn(fen: string): string | undefined
```

**Returns:** The updated FEN string for the next player's turn, or `undefined` if the FEN is invalid.

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
