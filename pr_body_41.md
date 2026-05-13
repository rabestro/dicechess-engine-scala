Closes #41

## Context
As we begin the **v0.2 - Move Generation** milestone, we need an extremely lightweight representation for chess moves to prevent memory allocation overhead and GC pauses during deep search operations.

## Objective
Implement `Move` as a 16-bit integer disguised via Scala 3 `opaque type`.

## Changes Made
- Created `src/main/scala/dicechess/engine/domain/Move.scala`.
- Defined `opaque type Move = Int`.
- Implemented the standard 16-bit layout:
  - **Bits 0-5**: Destination square (0-63)
  - **Bits 6-11**: Source square (0-63)
  - **Bits 12-15**: 4-bit Flag for Special Moves (Quiet, Double Push, Castling, Captures, Promotions).
- Provided Extension methods for `Move` to instantly extract `.fromSquare`, `.toSquare`, `.isCapture`, `.isPromotion`, etc. without any runtime object wrapping overhead.
- Implemented `MoveSpec.scala` MUnit tests to verify bitmask logic.

## Definition of Done (DoD)
- [x] 16-bit encoding implemented.
- [x] Extractors and factory methods built.
- [x] Unit tests verify lossless properties.
- [x] Passes `mise run check` successfully.
