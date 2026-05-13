Closes #43

## Context
As part of the **v0.2 - Move Generation (Classic)** milestone, we need to efficiently calculate pawn moves. Unlike leapers which are evaluated square-by-square, pawns of the same color can be shifted as an entire front line simultaneously using Bitboard arithmetic.

## Objective
Implement pawn push and capture generation using Bitboard operations, accounting for color direction, edge wrap-arounds, and double-push rank restrictions.

## Changes Made
- Created `src/main/scala/dicechess/engine/movegen/PawnGeneration.scala`.
- Implemented `singlePushes` and `doublePushes` methods that shift the entire pawn bitboard (`<< 8` for White, `>>> 8` for Black) and filter by `emptySquares`.
- Restricted `doublePushes` by applying a `Rank3` / `Rank6` mask to the `singlePushes` result, ensuring only pawns starting from their initial rank can double-jump.
- Implemented `eastCaptures` and `westCaptures` using `NotAFile` and `NotHFile` masks to prevent edge wrap-arounds (e.g. an H-file pawn wrapping to the A-file when capturing East).
- Created `src/test/scala/dicechess/engine/movegen/PawnGenerationSpec.scala` with comprehensive MUnit tests validating pushes, blocks, and capture boundaries for both White and Black.

## Definition of Done (DoD)
- [x] Single pushes and double pushes implemented.
- [x] East/West captures logic implemented with wrap-around prevention.
- [x] Unit tests verify pawn generation without illegal wrap-arounds.
- [x] Tested with `mise run check`.
