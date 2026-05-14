package dicechess.engine.movegen

import dicechess.engine.domain.*
import scala.collection.mutable

/** Unified move generator for Dice Chess. Coordinates between specialized generators for pawns, leapers, and sliding
  * pieces.
  */
object MoveGenerator {

  /** Generates all pseudo-legal moves for the active color and a given dice roll. */
  def generateMoves(state: GameState, diceRoll: Int): List[Move] = {
    val optPieceType = PieceType.fromDice(diceRoll)
    if (optPieceType.isEmpty) return Nil
    generatePieceMoves(state, optPieceType.get)
  }

  /** Generates all pseudo-legal moves for all pieces of the active color (standard chess). */
  def generateAllMoves(state: GameState): List[Move] = {
    PieceType.all.flatMap(pt => generatePieceMoves(state, pt))
  }

  private def generatePieceMoves(state: GameState, pieceType: PieceType): List[Move] = {
    val color        = state.activeColor
    val isWhite      = color.isWhite
    val enemyPieces  = if (isWhite) state.blackPieces else state.whitePieces
    val allPieces    = state.whitePieces | state.blackPieces
    val emptySquares = ~allPieces

    pieceType match {
      case PieceType.Pawn   => generatePawnMoves(state, color, emptySquares, enemyPieces)
      case PieceType.Knight => generateLeaperMoves(state, PieceType.Knight, color, enemyPieces)
      case PieceType.Bishop => generateSlidingMoves(state, PieceType.Bishop, color, allPieces, enemyPieces)
      case PieceType.Rook   => generateSlidingMoves(state, PieceType.Rook, color, allPieces, enemyPieces)
      case PieceType.Queen  => generateSlidingMoves(state, PieceType.Queen, color, allPieces, enemyPieces)
      case PieceType.King   => generateLeaperMoves(state, PieceType.King, color, enemyPieces)
      case _                => Nil
    }
  }

  private def generatePawnMoves(
      state: GameState,
      color: Color,
      emptySquares: Bitboard,
      enemies: Bitboard
  ): List[Move] = {
    val isWhite = color.isWhite
    val myPawns = if (isWhite) state.whitePieces & state.pawns else state.blackPieces & state.pawns
    if (myPawns.isEmpty) return Nil

    val moves = List.newBuilder[Move]
    var p     = myPawns.value
    while (p != 0) {
      val fromIdx = java.lang.Long.numberOfTrailingZeros(p)
      val from    = Square.fromIndex(fromIdx)
      val fromBB  = Bitboard.fromSquare(from)

      // --- Single Push ---
      val push1     = PawnGeneration.singlePushes(fromBB, emptySquares, color)
      val push1Prom = PawnGeneration.promotionSquares(push1, color)
      val push1Std  = PawnGeneration.nonPromotionSquares(push1, color)

      if (!push1Prom.isEmpty) {
        val to = Square.fromIndex(java.lang.Long.numberOfTrailingZeros(push1Prom.value))
        moves += Move(from, to, Move.QueenPromotion)
        moves += Move(from, to, Move.RookPromotion)
        moves += Move(from, to, Move.BishopPromotion)
        moves += Move(from, to, Move.KnightPromotion)
      }

      if (!push1Std.isEmpty) {
        val to1 = Square.fromIndex(java.lang.Long.numberOfTrailingZeros(push1Std.value))
        moves += Move(from, to1, Move.QuietMove)

        // Double Push
        val push2 = PawnGeneration.doublePushes(push1Std, emptySquares, color)
        if (!push2.isEmpty) {
          val to2 = Square.fromIndex(java.lang.Long.numberOfTrailingZeros(push2.value))
          moves += Move(from, to2, Move.DoublePawnPush)
        }
      }

      // --- Captures ---
      val east = PawnGeneration.eastCaptures(fromBB, enemies, color)
      val west = PawnGeneration.westCaptures(fromBB, enemies, color)

      def addCaptures(targets: Bitboard): Unit = {
        val prom = PawnGeneration.promotionSquares(targets, color)
        val std  = PawnGeneration.nonPromotionSquares(targets, color)

        var cp = prom.value
        while (cp != 0) {
          val to = Square.fromIndex(java.lang.Long.numberOfTrailingZeros(cp))
          moves += Move(from, to, Move.QueenPromoCapture)
          moves += Move(from, to, Move.RookPromoCapture)
          moves += Move(from, to, Move.BishopPromoCapture)
          moves += Move(from, to, Move.KnightPromoCapture)
          cp &= cp - 1
        }

        var cs = std.value
        while (cs != 0) {
          val to = Square.fromIndex(java.lang.Long.numberOfTrailingZeros(cs))
          moves += Move(from, to, Move.Capture)
          cs &= cs - 1
        }
      }

      addCaptures(east)
      addCaptures(west)

      // --- En Passant ---
      state.enPassant.foreach { epSquare =>
        val epBB     = Bitboard.fromSquare(epSquare)
        val epEast   = PawnGeneration.eastCaptures(fromBB, epBB, color)
        val epWest   = PawnGeneration.westCaptures(fromBB, epBB, color)
        val epTarget = epEast | epWest
        if (!epTarget.isEmpty) {
          moves += Move(from, epSquare, Move.EnPassantCapture)
        }
      }

      p &= p - 1
    }
    moves.result()
  }

  private def generateLeaperMoves(state: GameState, pt: PieceType, color: Color, enemies: Bitboard): List[Move] = {
    val myPieces     = if (color.isWhite) state.whitePieces else state.blackPieces
    val typeBB       = if (pt == PieceType.Knight) state.knights else state.kings
    val activePieces = myPieces & typeBB

    val moves = List.newBuilder[Move]
    var p     = activePieces.value
    while (p != 0) {
      val fromIdx = java.lang.Long.numberOfTrailingZeros(p)
      val from    = Square.fromIndex(fromIdx)

      val attacks =
        if (pt == PieceType.Knight) LeaperAttacks.knightAttacksFor(from) else LeaperAttacks.kingAttacksFor(from)
      val legal = attacks & ~myPieces

      var a = legal.value
      while (a != 0) {
        val toIdx = java.lang.Long.numberOfTrailingZeros(a)
        val to    = Square.fromIndex(toIdx)
        val flags = if (enemies.contains(to)) Move.Capture else Move.QuietMove
        moves += Move(from, to, flags)
        a &= a - 1
      }

      // --- Castling (only for King) ---
      if (pt == PieceType.King) {
        generateCastlingMoves(state, color, moves)
      }

      p &= p - 1
    }
    moves.result()
  }

  private def generateCastlingMoves(state: GameState, color: Color, moves: mutable.Builder[Move, List[Move]]): Unit = {
    val rights    = state.castlingRights
    val allPieces = state.whitePieces | state.blackPieces
    val enemy     = color.opponent

    if (color.isWhite) {
      // King Side
      if (rights.contains('K')) {
        val pathEmpty = !allPieces.contains(Square('f', 1)) && !allPieces.contains(Square('g', 1))
        if (pathEmpty) {
          val safe = isSquareAttacked(state, Square('e', 1), enemy).isEmpty &&
            isSquareAttacked(state, Square('f', 1), enemy).isEmpty &&
            isSquareAttacked(state, Square('g', 1), enemy).isEmpty
          if (safe) moves += Move(Square('e', 1), Square('g', 1), Move.KingCastle)
        }
      }
      // Queen Side
      if (rights.contains('Q')) {
        val pathEmpty =
          !allPieces.contains(Square('b', 1)) && !allPieces.contains(Square('c', 1)) && !allPieces.contains(
            Square('d', 1)
          )
        if (pathEmpty) {
          val safe = isSquareAttacked(state, Square('e', 1), enemy).isEmpty &&
            isSquareAttacked(state, Square('d', 1), enemy).isEmpty &&
            isSquareAttacked(state, Square('c', 1), enemy).isEmpty
          if (safe) moves += Move(Square('e', 1), Square('c', 1), Move.QueenCastle)
        }
      }
    } else {
      // King Side
      if (rights.contains('k')) {
        val pathEmpty = !allPieces.contains(Square('f', 8)) && !allPieces.contains(Square('g', 8))
        if (pathEmpty) {
          val safe = isSquareAttacked(state, Square('e', 8), enemy).isEmpty &&
            isSquareAttacked(state, Square('f', 8), enemy).isEmpty &&
            isSquareAttacked(state, Square('g', 8), enemy).isEmpty
          if (safe) moves += Move(Square('e', 8), Square('g', 8), Move.KingCastle)
        }
      }
      // Queen Side
      if (rights.contains('q')) {
        val pathEmpty =
          !allPieces.contains(Square('b', 8)) && !allPieces.contains(Square('c', 8)) && !allPieces.contains(
            Square('d', 8)
          )
        if (pathEmpty) {
          val safe = isSquareAttacked(state, Square('e', 8), enemy).isEmpty &&
            isSquareAttacked(state, Square('d', 8), enemy).isEmpty &&
            isSquareAttacked(state, Square('c', 8), enemy).isEmpty
          if (safe) moves += Move(Square('e', 8), Square('c', 8), Move.QueenCastle)
        }
      }
    }
  }

  private def generateSlidingMoves(
      state: GameState,
      pt: PieceType,
      color: Color,
      allPieces: Bitboard,
      enemies: Bitboard
  ): List[Move] = {
    val myPieces = if (color.isWhite) state.whitePieces else state.blackPieces
    val typeBB   = pt match {
      case PieceType.Bishop => state.bishops
      case PieceType.Rook   => state.rooks
      case PieceType.Queen  => state.queens
      case _                => Bitboard.empty
    }
    val activePieces = myPieces & typeBB

    val moves = List.newBuilder[Move]
    var p     = activePieces.value
    while (p != 0) {
      val fromIdx = java.lang.Long.numberOfTrailingZeros(p)
      val from    = Square.fromIndex(fromIdx)

      val attacks = pt match {
        case PieceType.Bishop => MagicBitboards.bishopAttacks(from, allPieces)
        case PieceType.Rook   => MagicBitboards.rookAttacks(from, allPieces)
        case PieceType.Queen  => MagicBitboards.queenAttacks(from, allPieces)
        case _                => Bitboard.empty
      }
      val legal = attacks & ~myPieces

      var a = legal.value
      while (a != 0) {
        val toIdx = java.lang.Long.numberOfTrailingZeros(a)
        val to    = Square.fromIndex(toIdx)
        val flags = if (enemies.contains(to)) Move.Capture else Move.QuietMove
        moves += Move(from, to, flags)
        a &= a - 1
      }
      p &= p - 1
    }
    moves.result()
  }

  /** Returns true if the given square is attacked by any piece of the opponent.
    *
    * Used for castling legality checks and king safety.
    */
  def isSquareAttacked(state: GameState, sq: Square, attackerColor: Color): Bitboard = {
    val allPieces       = state.whitePieces | state.blackPieces
    val attackers       = if (attackerColor.isWhite) state.whitePieces else state.blackPieces
    val attackerPawns   = attackers & state.pawns
    val attackerKnights = attackers & state.knights
    val attackerBishops = attackers & (state.bishops | state.queens)
    val attackerRooks   = attackers & (state.rooks | state.queens)
    val attackerKings   = attackers & state.kings

    // Pawn attacks (from the perspective of the square being attacked)
    val pawnAttacks = PawnGeneration.anyAttacks(Bitboard.fromSquare(sq), attackerColor.opponent) & attackerPawns
    if (!pawnAttacks.isEmpty) return pawnAttacks

    // Knight attacks
    val knightAttacks = LeaperAttacks.knightAttacksFor(sq) & attackerKnights
    if (!knightAttacks.isEmpty) return knightAttacks

    // Sliding attacks
    val bishopAttacks = MagicBitboards.bishopAttacks(sq, allPieces) & attackerBishops
    if (!bishopAttacks.isEmpty) return bishopAttacks

    val rookAttacks = MagicBitboards.rookAttacks(sq, allPieces) & attackerRooks
    if (!rookAttacks.isEmpty) return rookAttacks

    // King attacks
    val kingAttacks = LeaperAttacks.kingAttacksFor(sq) & attackerKings
    if (!kingAttacks.isEmpty) return kingAttacks

    Bitboard.empty
  }
}
