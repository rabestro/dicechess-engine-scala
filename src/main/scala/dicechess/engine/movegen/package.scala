package dicechess.engine

/** Move generation for the Dice Chess Engine.
  *
  * This package implements a fully bitboard-based pseudo-legal move generator. Each subsystem is responsible for a
  * specific piece class, and [[MoveGenerator]] orchestrates them into a unified interface.
  *
  * ## Architecture
  *
  * | Object             | Responsibility                                              |
  * |:-------------------|:------------------------------------------------------------|
  * | [[PawnGeneration]] | Parallel push/capture generation via bitboard shifts        |
  * | [[LeaperAttacks]]  | Precomputed attack tables for Knights and Kings             |
  * | [[MagicBitboards]] | O(1) sliding-piece attacks via magic-number hashing         |
  * | [[MoveGenerator]]  | Unified entry point; serialises bitboards into `Move` lists |
  *
  * ## Move representation
  *
  * All generated moves are 16-bit [[dicechess.engine.domain.Move]] values encoding origin square, destination square,
  * and a 4-bit flag field (quiet, capture, castling, en passant, promotion).
  *
  * ## Pseudo-legal generation
  *
  * The generator produces *pseudo-legal* moves — moves that obey piece movement rules but do not verify that the
  * resulting position leaves the king in check. Full legality filtering is applied by the search layer.
  */
package object movegen
