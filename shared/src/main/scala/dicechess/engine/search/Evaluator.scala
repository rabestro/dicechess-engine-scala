package dicechess.engine.search

import dicechess.engine.domain.{Color, GameState, Bitboard}

object Evaluator:
  /** Evaluates the material balance for the active color.
    *
    * Piece values:
    *   - King: 10000
    *   - Queen: 900
    *   - Rook: 500
    *   - Bishop: 300
    *   - Knight: 300
    *   - Pawn: 100
    *
    * @param state
    *   the current GameState
    * @param color
    *   the perspective from which to evaluate
    * @return
    *   positive integer if `color` has advantage, negative if disadvantage
    */
  def evaluateMaterial(state: GameState, color: Color): Int =
    val myPieces  = if color.isWhite then state.whitePieces else state.blackPieces
    val oppPieces = if color.isWhite then state.blackPieces else state.whitePieces

    scoreBitboard(state, myPieces) - scoreBitboard(state, oppPieces)

  private def scoreBitboard(state: GameState, bb: Bitboard): Int =
    var s = 0
    s += (bb & state.pawns).count * 100
    s += (bb & state.knights).count * 300
    s += (bb & state.bishops).count * 300
    s += (bb & state.rooks).count * 500
    s += (bb & state.queens).count * 900
    s += (bb & state.kings).count * 10000
    s
