---
title: Testing Strategy & DSL
description: How we ensure correctness of the move generator using human-readable Test DSL.
---

In a chess engine, the core logic (move generation, board representation) is performance-critical and heavily bitwise. Testing these components using raw hexadecimal masks or bit shifts is error-prone and difficult for humans to verify.

To solve this, we use a **Test Domain-Specific Language (DSL)** that allows us to write tests in plain chess notation or even visual ASCII boards.

## The Test DSL

Our testing utilities live in `src/test/scala/dicechess/engine/testutils/TestBoard.scala`. They provide three primary ways to interact with bitboards.

### 1. Visual ASCII Boards

For complex scenarios like **Magic Bitboards** (where blockers determine the range of sliding pieces), we can define the board state visually.

```scala
import dicechess.engine.testutils.TestBoard

val pos = TestBoard.fromAscii("""
  - - - - - - - -
  - - - - - - - -
  - - - - P - - -
  - - - - - - - -
  - - P - R - P -
  - - - - - - - -
  - - - - P - - -
  - - - - - - - -
""")

val occupancy = pos.occupied // Bitboard containing all 'P' and 'R' positions
```

The parser uses FEN-standard characters (`P`, `R`, `k`, etc.) and ignores whitespace, dots, and dashes.

### 2. Algebraic Varargs

When you just need to place a few pieces or verify specific targets, you can use the varargs constructor:

```scala
// Creates a bitboard with bits set at d3 and f5
val occupancy = TestBoard("d3", "f5")
```

### 3. Extension Methods

For maximum brevity, we provide extension methods on standard `String` literals:

```scala
import dicechess.engine.testutils.TestBoard.*

val sq = "e4".sq  // Returns a Square object
val bb = "e4".bb  // Returns a Bitboard with e4 set
```

## Example: Testing a Rook Attack

Here is how a real test looks using the DSL:

```scala
test("Rook attacks with blockers") {
  val sq = "e4".sq
  
  val pos = TestBoard.fromAscii("""
    - - - - - - - -
    - - - - - - - -
    - - - - P - - -
    - - - - - - - -
    - - P - R - P -
    - - - - - - - -
    - - - - P - - -
    - - - - - - - -
  """)
  
  val attacks = MagicBitboards.rookAttacks(sq, pos.occupied)
  
  val expected = TestBoard(
    "e5", "e6", // North
    "e3", "e2", // South
    "d4", "c4", // West
    "f4", "g4"  // East
  )
  
  assertEquals(attacks, expected)
}
```

## Why this matters

1. **Self-Documenting**: The tests show exactly what is being tested without requiring comments.
2. **Fast Debugging**: If a test fails, the diff between two `Bitboard` objects is rendered by MUnit, and you can easily cross-reference it with the visual board in the test code.
3. **Correctness**: It is much harder to make a mistake when typing `"e4"` than when typing `1L << 28`.
