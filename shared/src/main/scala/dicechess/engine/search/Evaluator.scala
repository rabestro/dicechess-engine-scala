package dicechess.engine.search

import dicechess.engine.domain.{Color, GameState, Bitboard}

/** Pure material-balance evaluator for Dice Chess positions.
  *
  * Assigns piece values (in centipawns) and returns the difference between the active side's material and the
  * opponent's. This is a simple but effective heuristic for greedy search and forms the base layer for any future
  * positional or probability-weighted evaluation.
  *
  * ## Piece values
  *
  * | Piece  | Value |
  * |:-------|------:|
  * | Pawn   |   100 |
  * | Knight |   300 |
  * | Bishop |   300 |
  * | Rook   |   500 |
  * | Queen  |   900 |
  * | King   | 10000 |
  */
object Evaluator:
  /** Evaluates the material balance for the given `color`.
    *
    * A positive return value indicates that `color` has more material than the opponent; a negative value indicates a
    * material deficit.
    *
    * @param state
    *   the current [[GameState]]
    * @param color
    *   the perspective from which to evaluate (typically `state.activeColor`)
    * @return
    *   signed centipawn score: positive means `color` is ahead, negative means behind
    */
  def evaluateMaterial(state: GameState, color: Color): Int =
    val myPieces  = if color.isWhite then state.whitePieces else state.blackPieces
    val oppPieces = if color.isWhite then state.blackPieces else state.whitePieces

    scoreBitboard(state, myPieces) - scoreBitboard(state, oppPieces)

  /** Computes the total centipawn value of all pieces in `bb`.
    *
    * Intersects `bb` with each piece-type bitboard to count pieces by type, then multiplies each count by the
    * corresponding fixed piece value.
    *
    * @param state
    *   the current [[GameState]] providing piece-type bitboards
    * @param bb
    *   the subset of squares to score (typically one side's pieces)
    * @return
    *   the total centipawn value of pieces on squares in `bb`
    */
  private def scoreBitboard(state: GameState, bb: Bitboard): Int =
    var s = 0
    s += (bb & state.pawns).count * 100
    s += (bb & state.knights).count * 300
    s += (bb & state.bishops).count * 300
    s += (bb & state.rooks).count * 500
    s += (bb & state.queens).count * 900
    s += (bb & state.kings).count * 10000
    s
