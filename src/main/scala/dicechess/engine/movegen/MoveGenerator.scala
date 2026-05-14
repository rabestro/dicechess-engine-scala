package dicechess.engine.movegen

import dicechess.engine.domain.*

/** Unified move generator for Dice Chess. Coordinates between specialized generators for pawns, leapers, and sliding
  * pieces.
  */
object MoveGenerator {

  /** Generates all pseudo-legal micro-moves for the active color and a given dice roll.
    */
  def generateMoves(state: GameState, diceRoll: Int): List[MicroMove] = {
    val optPieceType = PieceType.fromDice(diceRoll)
    if (optPieceType.isEmpty) return Nil
    val pieceType = optPieceType.get

    val color   = state.activeColor
    val isWhite = color.isWhite

    val friendlyPieces = if (isWhite) state.whitePieces else state.blackPieces
    val enemyPieces    = if (isWhite) state.blackPieces else state.whitePieces
    val allPieces      = state.whitePieces | state.blackPieces
    val emptySquares   = ~allPieces

    pieceType match {
      case PieceType.Pawn   => generatePawnMoves(state, color, emptySquares, enemyPieces)
      case PieceType.Knight => generateLeaperMoves(state, PieceType.Knight, friendlyPieces)
      case PieceType.Bishop => generateSlidingMoves(state, PieceType.Bishop, allPieces, friendlyPieces)
      case PieceType.Rook   => generateSlidingMoves(state, PieceType.Rook, allPieces, friendlyPieces)
      case PieceType.Queen  => generateSlidingMoves(state, PieceType.Queen, allPieces, friendlyPieces)
      case PieceType.King   => generateLeaperMoves(state, PieceType.King, friendlyPieces)
      case _                => Nil
    }
  }

  /** Promotion piece types emitted for every pawn that reaches the back rank. */
  private val PromotionPieces: List[PieceType] =
    List(PieceType.Queen, PieceType.Rook, PieceType.Bishop, PieceType.Knight)

  private def generatePawnMoves(
      state: GameState,
      color: Color,
      emptySquares: Bitboard,
      enemies: Bitboard
  ): List[MicroMove] = {
    val isWhite = color.isWhite
    val myPawns = if (isWhite) state.whitePieces & state.pawns else state.blackPieces & state.pawns
    if (myPawns.isEmpty) return Nil

    val moves = List.newBuilder[MicroMove]
    var p     = myPawns.value
    while (p != 0) {
      val fromIdx = java.lang.Long.numberOfTrailingZeros(p)
      val from    = Square.fromIndex(fromIdx)
      val fromBB  = Bitboard.fromSquare(from)

      // --- Single Push: split into promotions and standard moves ---
      val push1     = PawnGeneration.singlePushes(fromBB, emptySquares, color)
      val push1Prom = PawnGeneration.promotionSquares(push1, color)
      val push1Std  = PawnGeneration.nonPromotionSquares(push1, color)

      // Emit 4 promotion moves for each promotion target
      if (!push1Prom.isEmpty) {
        val to = Square.fromIndex(java.lang.Long.numberOfTrailingZeros(push1Prom.value))
        PromotionPieces.foreach(pt => moves += MicroMove(from, to, Some(pt)))
      }

      // Emit a standard quiet move (and potential double push)
      if (!push1Std.isEmpty) {
        val to1 = Square.fromIndex(java.lang.Long.numberOfTrailingZeros(push1Std.value))
        moves += MicroMove(from, to1)

        // Double Push (only possible from starting rank, never a promotion rank)
        val push2 = PawnGeneration.doublePushes(push1Std, emptySquares, color)
        if (!push2.isEmpty) {
          val to2 = Square.fromIndex(java.lang.Long.numberOfTrailingZeros(push2.value))
          moves += MicroMove(from, to2)
        }
      }

      // --- Captures: split into promotions and standard captures ---
      val captures = PawnGeneration.eastCaptures(fromBB, enemies, color) |
        PawnGeneration.westCaptures(fromBB, enemies, color)
      val capturesProm = PawnGeneration.promotionSquares(captures, color)
      val capturesStd  = PawnGeneration.nonPromotionSquares(captures, color)

      // Emit 4 promotion-capture moves for each promotion capture target
      var cp = capturesProm.value
      while (cp != 0) {
        val toIdx = java.lang.Long.numberOfTrailingZeros(cp)
        val to    = Square.fromIndex(toIdx)
        PromotionPieces.foreach(pt => moves += MicroMove(from, to, Some(pt)))
        cp &= cp - 1
      }

      // Emit standard captures
      var cs = capturesStd.value
      while (cs != 0) {
        val toIdx = java.lang.Long.numberOfTrailingZeros(cs)
        moves += MicroMove(from, Square.fromIndex(toIdx))
        cs &= cs - 1
      }

      p &= p - 1
    }
    moves.result()
  }

  private def generateLeaperMoves(state: GameState, pt: PieceType, friendlyPieces: Bitboard): List[MicroMove] = {
    val myColorPieces    = if (state.activeColor.isWhite) state.whitePieces else state.blackPieces
    val typeBB: Bitboard = if (pt == PieceType.Knight) state.knights else state.kings
    val specificPieces   = myColorPieces & typeBB

    val moves = List.newBuilder[MicroMove]
    var p     = specificPieces.value
    while (p != 0) {
      val fromIdx = java.lang.Long.numberOfTrailingZeros(p)
      val from    = Square.fromIndex(fromIdx)

      val attacks =
        if (pt == PieceType.Knight)
          LeaperAttacks.knightAttacksFor(from)
        else LeaperAttacks.kingAttacksFor(from)

      val legalAttacks = attacks & ~friendlyPieces

      var a = legalAttacks.value
      while (a != 0) {
        val toIdx = java.lang.Long.numberOfTrailingZeros(a)
        moves += MicroMove(from, Square.fromIndex(toIdx))
        a &= a - 1
      }
      p &= p - 1
    }
    moves.result()
  }

  private def generateSlidingMoves(
      state: GameState,
      pt: PieceType,
      allPieces: Bitboard,
      friendlyPieces: Bitboard
  ): List[MicroMove] = {
    val myColorPieces    = if (state.activeColor.isWhite) state.whitePieces else state.blackPieces
    val typeBB: Bitboard = pt match {
      case PieceType.Bishop => state.bishops
      case PieceType.Rook   => state.rooks
      case PieceType.Queen  => state.queens
      case _                => Bitboard.empty
    }
    val specificPieces = myColorPieces & typeBB

    val moves = List.newBuilder[MicroMove]
    var p     = specificPieces.value
    while (p != 0) {
      val fromIdx = java.lang.Long.numberOfTrailingZeros(p)
      val from    = Square.fromIndex(fromIdx)

      val attacks = pt match {
        case PieceType.Bishop => MagicBitboards.bishopAttacks(from, allPieces)
        case PieceType.Rook   => MagicBitboards.rookAttacks(from, allPieces)
        case PieceType.Queen  => MagicBitboards.queenAttacks(from, allPieces)
        case _                => Bitboard.empty
      }

      val legalAttacks = attacks & ~friendlyPieces

      var a = legalAttacks.value
      while (a != 0) {
        val toIdx = java.lang.Long.numberOfTrailingZeros(a)
        moves += MicroMove(from, Square.fromIndex(toIdx))
        a &= a - 1
      }
      p &= p - 1
    }
    moves.result()
  }
}
