package dicechess.engine.movegen

import dicechess.engine.domain.*
import scala.collection.mutable

/** Unified move generator for Dice Chess. Coordinates between specialized generators for pawns, leapers, and sliding
  * pieces.
  */
object MoveGenerator {

  /** Generates all pseudo-legal moves for the active color and a given dice roll.
    *
    * Maps the dice roll to a [[PieceType]] via [[PieceType.fromDice]] and delegates to [[generatePieceMoves]]. Returns
    * `Nil` immediately for invalid rolls (0 or 7+).
    *
    * @param state
    *   current game state
    * @param diceRoll
    *   value in `[1, 6]`; values outside this range yield an empty list
    * @return
    *   pseudo-legal moves for the piece type indicated by the dice roll
    */
  def generateMoves(state: GameState, diceRoll: Int): List[Move] = {
    val optPieceType = PieceType.fromDice(diceRoll)
    if (optPieceType.isEmpty) return Nil
    generatePieceMoves(state, optPieceType.get)
  }

  /** Generates all pseudo-legal moves for all pieces of the active color (standard chess).
    *
    * Iterates over every [[PieceType]] and concatenates the results of [[generatePieceMoves]]. Useful for perft testing
    * and for positions where a dice roll is not applicable.
    *
    * @param state
    *   current game state
    * @return
    *   combined pseudo-legal move list for all piece types
    */
  def generateAllMoves(state: GameState): List[Move] = {
    PieceType.all.flatMap(pt => generatePieceMoves(state, pt))
  }

  /** Dispatches move generation to the correct subsystem for `pieceType`. */
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

  /** Generates pseudo-legal pawn moves for the active side.
    *
    * Produces (per pawn):
    *
    *   - **Single push** — quiet move, or four promotion moves on the back rank.
    *   - **Double push** — only from the starting rank, sets the en-passant square flag.
    *   - **Diagonal captures** (east and west) — standard or promotion captures.
    *   - **En-passant capture** — when [[GameState.enPassant]] is set and the pawn can reach it.
    *
    * Returns early with `Nil` when the active side has no pawns.
    */
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
      addPawnCaptures(from, east, color, moves)
      addPawnCaptures(from, west, color, moves)

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

  /** Generates pseudo-legal moves for a leaper piece (Knight or King).
    *
    * Looks up precomputed attack masks from [[LeaperAttacks]], masks out friendly pieces, and emits quiet or capture
    * moves. When `pt` is `King`, also calls [[generateCastlingMoves]] after the normal attack enumeration.
    */
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

  /** Appends castling moves (king-side and queen-side) for `color` to `moves`.
    *
    * Delegates each side to [[tryCastle]], which checks castling rights, path clearance, and king-safety.
    */
  private def generateCastlingMoves(state: GameState, color: Color, moves: mutable.Builder[Move, List[Move]]): Unit = {
    val allPieces        = state.whitePieces | state.blackPieces
    val enemy            = color.opponent
    val rank             = if color.isWhite then 1 else 8
    val (kRight, qRight) = if color.isWhite then ('K', 'Q') else ('k', 'q')
    tryCastle(state, allPieces, enemy, moves, kRight, rank, kingSide = true)
    tryCastle(state, allPieces, enemy, moves, qRight, rank, kingSide = false)
  }

  /** Appends a castling move if all three preconditions are satisfied:
    *
    *   1. The castling right character (`K`, `Q`, `k`, or `q`) is present in [[GameState.castlingRights]].
    *   2. Every square on the rook's path between king and rook is empty.
    *   3. None of the king's transit squares (origin, pass-through, destination) are attacked by `enemy`.
    *
    * @param right
    *   castling-right character to check (e.g. `'K'` for White king-side)
    * @param rank
    *   back rank of the castling side (1 for White, 8 for Black)
    * @param kingSide
    *   `true` for king-side (O-O), `false` for queen-side (O-O-O)
    */
  private def tryCastle(
      state: GameState,
      allPieces: Bitboard,
      enemy: Color,
      moves: mutable.Builder[Move, List[Move]],
      right: Char,
      rank: Int,
      kingSide: Boolean
  ): Unit =
    if state.castlingRights.contains(right) then
      val (path, safe, kingTo, flag) =
        if kingSide then
          (
            List(Square('f', rank), Square('g', rank)),
            List(Square('e', rank), Square('f', rank), Square('g', rank)),
            Square('g', rank),
            Move.KingCastle
          )
        else
          (
            List(Square('b', rank), Square('c', rank), Square('d', rank)),
            List(Square('e', rank), Square('d', rank), Square('c', rank)),
            Square('c', rank),
            Move.QueenCastle
          )
      if path.forall(!allPieces.contains(_)) && safe.forall(sq => isSquareAttacked(state, sq, enemy).isEmpty) then
        moves += Move(Square('e', rank), kingTo, flag)

  /** Generates pseudo-legal moves for a sliding piece (Bishop, Rook, or Queen).
    *
    * Uses [[MagicBitboards]] for O(1) attack-set lookup, then masks out friendly pieces and emits quiet or capture
    * moves.
    */
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

  /** Appends pawn capture moves from `from` to every set bit in `targets`.
    *
    * Promotion-rank captures expand to four moves each (Queen, Rook, Bishop, Knight). All other captures emit a single
    * `Capture` move.
    *
    * @param from
    *   square the capturing pawn is on
    * @param targets
    *   bitboard of reachable enemy squares
    * @param color
    *   color of the capturing pawn (used to identify the promotion rank)
    */
  private def addPawnCaptures(
      from: Square,
      targets: Bitboard,
      color: Color,
      moves: mutable.Builder[Move, List[Move]]
  ): Unit = {
    val prom = PawnGeneration.promotionSquares(targets, color)
    val std  = PawnGeneration.nonPromotionSquares(targets, color)
    var cp   = prom.value
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

  /** Returns the set of `attackerColor` pieces that attack `sq`, or an empty bitboard if none do.
    *
    * Checks attack patterns in priority order: pawns → knights → diagonal sliders (bishop/queen) → orthogonal sliders
    * (rook/queen) → king. Returns the first non-empty attacker set found, which is sufficient for legality checks.
    *
    * Used for castling path safety and king-in-check detection.
    *
    * @param state
    *   current game state
    * @param sq
    *   the square to test
    * @param attackerColor
    *   the color whose pieces may be attacking `sq`
    * @return
    *   bitboard of attacking pieces, or [[Bitboard.empty]] if the square is safe
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
