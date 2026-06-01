package dicechess.engine.search

import dicechess.engine.domain.*

trait DrawOfferLogic extends SearchAlgorithm:

  override def shouldOfferDraw(state: GameState): Boolean =
    hasInsufficientDrawMaterial(state)

  override def shouldAcceptDraw(state: GameState): Boolean =
    Evaluator.evaluate(state, state.activeColor) < -200

  private def hasInsufficientDrawMaterial(state: GameState): Boolean =
    val allPieces = state.pawns | state.knights | state.rooks | state.queens
    if allPieces != Bitboard.empty then return false

    val whiteBishops = (state.bishops & state.whitePieces).count
    val blackBishops = (state.bishops & state.blackPieces).count

    if whiteBishops > 1 || blackBishops > 1 then return false

    val wkSq = Square.fromIndex(java.lang.Long.numberOfTrailingZeros((state.kings & state.whitePieces).value))
    val bkSq = Square.fromIndex(java.lang.Long.numberOfTrailingZeros((state.kings & state.blackPieces).value))

    val fileDist = (wkSq.file - bkSq.file).abs
    val rankDist = (wkSq.rank - bkSq.rank).abs

    if fileDist <= 1 && rankDist <= 1 then return false

    val activeColor         = state.activeColor
    val opponentColor       = activeColor.opponent
    val ourPieces: Bitboard = if activeColor.isWhite then state.whitePieces else state.blackPieces
    val oppPieces: Bitboard = if opponentColor.isWhite then state.whitePieces else state.blackPieces
    val opponentBishopCount = if opponentColor.isWhite then whiteBishops else blackBishops

    opponentBishopCount == 0 || {
      val ourKingSq   = Square.fromIndex(java.lang.Long.numberOfTrailingZeros((state.kings & ourPieces).value))
      val oppBishopSq = Square.fromIndex(java.lang.Long.numberOfTrailingZeros((state.bishops & oppPieces).value))
      ((ourKingSq.file - 'a' + ourKingSq.rank) & 1) != ((oppBishopSq.file - 'a' + oppBishopSq.rank) & 1)
    }
