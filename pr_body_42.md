Closes #42

## Context
As part of the **v0.2 - Move Generation (Classic)** milestone, we need to quickly generate attack sets for leaping pieces (Knights and Kings). Since they jump fixed distances and are not blocked by pieces in between, their attacks can be purely determined by their source square.

## Objective
Precompute attack tables for Knights and Kings into static arrays to guarantee `O(1)` memory lookups during move generation.

## Changes Made
- Upgraded the `Bitboard` opaque type in `Models.scala` to support the `<<` and `>>>` shift operators, as well as a raw `Long` constructor.
- Added `src/main/scala/dicechess/engine/movegen/LeaperAttacks.scala`.
- Configured 4 fundamental "Not-File" bitmasks (`NotAFile`, `NotABFile`, `NotHFile`, `NotGHFile`) to prevent illegal wrap-arounds across the edges of the 1D Bitboard.
- Generated `knightAttacks` and `kingAttacks` as `Array[Bitboard]` of size 64 initialized at JVM startup.
- Implemented `LeaperAttacksSpec.scala` MUnit tests to verify perfectly generated bitboards for center squares and corner squares (verifying wrap-around prevention).

## Definition of Done (DoD)
- [x] Precomputed `KnightAttacks` and `KingAttacks` arrays generated.
- [x] Fast retrieval methods built.
- [x] Unit tests verify exact attack patterns (edges, corners, and centers).
- [x] Tested with `mise run check`.
