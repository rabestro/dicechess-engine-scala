package dicechess.engine.domain

import scala.annotation.targetName

private object Position:
  def promotionPieceType(flags: Int): PieceType = flags match {
    case Move.KnightPromotion | Move.KnightPromoCapture => PieceType.Knight
    case Move.BishopPromotion | Move.BishopPromoCapture => PieceType.Bishop
    case Move.RookPromotion | Move.RookPromoCapture     => PieceType.Rook
    case _                                              => PieceType.Queen
  }

  def updatedCastlingRights(
      rights: String,
      movingPiece: Piece,
      from: Square,
      targetPiece: Option[Piece],
      to: Square,
      isWhite: Boolean
  ): String = {
    var r = rights
    if (movingPiece.pieceType == PieceType.King) {
      if (isWhite) r = r.filterNot(c => c == 'K' || c == 'Q')
      else r = r.filterNot(c => c == 'k' || c == 'q')
    } else if (movingPiece.pieceType == PieceType.Rook) {
      if (isWhite) {
        if (from == Square('a', 1)) r = r.filterNot(_ == 'Q')
        else if (from == Square('h', 1)) r = r.filterNot(_ == 'K')
      } else {
        if (from == Square('a', 8)) r = r.filterNot(_ == 'q')
        else if (from == Square('h', 8)) r = r.filterNot(_ == 'k')
      }
    }
    targetPiece.foreach { p =>
      if (p.pieceType == PieceType.Rook) {
        if (to == Square('a', 8)) r = r.filterNot(_ == 'q')
        else if (to == Square('h', 8)) r = r.filterNot(_ == 'k')
        else if (to == Square('a', 1)) r = r.filterNot(_ == 'Q')
        else if (to == Square('h', 1)) r = r.filterNot(_ == 'K')
      }
    }
    if (r.isEmpty) "-" else r
  }

extension (state: GameState)
  /** Applies a micro-move (Turn-based) to the current game state.
    *
    * This handles piece movement, captures, and basic bitboard updates. Note: This is a "raw" move application.
    * Higher-level logic (like switching turns after 3 micro-moves) should be handled by the Turn manager.
    */
  def makeMove(mv: MicroMove): GameState =
    val from        = mv.from
    val to          = mv.to
    val movingPiece = state.mailbox(from)
    val targetPiece = state.mailbox.get(to)

    val fromBB   = Bitboard.fromSquare(from)
    val toBB     = Bitboard.fromSquare(to)
    val fromToBB = fromBB | toBB

    // Update global piece bitboards for active color
    val isWhite  = movingPiece.color.isWhite
    var newWhite = state.whitePieces
    var newBlack = state.blackPieces
    if isWhite then newWhite ^= fromToBB else newBlack ^= fromToBB

    // If it's a capture, remove target piece from opponent's bitboard
    targetPiece.foreach { _ =>
      if isWhite then newBlack &= ~toBB else newWhite &= ~toBB
    }

    // Update piece-type bitboards
    var newPawns   = state.pawns
    var newKnights = state.knights
    var newBishops = state.bishops
    var newRooks   = state.rooks
    var newQueens  = state.queens
    var newKings   = state.kings

    // Remove from 'from' and potentially 'to' (if capture)
    def clear(bb: Bitboard, sqBB: Bitboard): Bitboard = bb & ~sqBB

    // Always clear 'from' for the moving piece type
    movingPiece.pieceType match
      case PieceType.Pawn   => newPawns = clear(newPawns, fromBB)
      case PieceType.Knight => newKnights = clear(newKnights, fromBB)
      case PieceType.Bishop => newBishops = clear(newBishops, fromBB)
      case PieceType.Rook   => newRooks = clear(newRooks, fromBB)
      case PieceType.Queen  => newQueens = clear(newQueens, fromBB)
      case PieceType.King   => newKings = clear(newKings, fromBB)

    // Clear 'to' for target piece type if it was a capture
    targetPiece.foreach { p =>
      p.pieceType match
        case PieceType.Pawn   => newPawns = clear(newPawns, toBB)
        case PieceType.Knight => newKnights = clear(newKnights, toBB)
        case PieceType.Bishop => newBishops = clear(newBishops, toBB)
        case PieceType.Rook   => newRooks = clear(newRooks, toBB)
        case PieceType.Queen  => newQueens = clear(newQueens, toBB)
        case PieceType.King   => newKings = clear(newKings, toBB)
    }

    // Add to 'to' for the moving piece (handling promotion if any)
    val finalPieceType = mv.promotion.getOrElse(movingPiece.pieceType)
    val promoBB        = toBB
    finalPieceType match
      case PieceType.Pawn   => newPawns |= promoBB
      case PieceType.Knight => newKnights |= promoBB
      case PieceType.Bishop => newBishops |= promoBB
      case PieceType.Rook   => newRooks |= promoBB
      case PieceType.Queen  => newQueens |= promoBB
      case PieceType.King   => newKings |= promoBB

    // Update mailbox
    var newMailbox = state.mailbox - from
    newMailbox += (to -> Piece(movingPiece.color, finalPieceType))

    state.copy(
      whitePieces = newWhite,
      blackPieces = newBlack,
      pawns = newPawns,
      knights = newKnights,
      bishops = newBishops,
      rooks = newRooks,
      queens = newQueens,
      kings = newKings,
      mailbox = newMailbox,
      halfMoveClock =
        if movingPiece.pieceType == PieceType.Pawn || targetPiece.isDefined then 0 else state.halfMoveClock + 1
    )

  /** Applies a Move (search-optimized) to the current game state.
    *
    * This handles all special chess rules like castling, en passant, and double pawn pushes using the move's flags.
    */
  @targetName("makeMove_Move")
  def makeMove(mv: Move): GameState = {
    val from        = mv.fromSquare
    val to          = mv.toSquare
    val movingPiece = state.mailbox(from)
    val color       = movingPiece.color
    val isWhite     = color.isWhite

    val fromBB   = Bitboard.fromSquare(from)
    val toBB     = Bitboard.fromSquare(to)
    val fromToBB = fromBB | toBB

    var newWhite   = state.whitePieces
    var newBlack   = state.blackPieces
    var newPawns   = state.pawns
    var newKnights = state.knights
    var newBishops = state.bishops
    var newRooks   = state.rooks
    var newQueens  = state.queens
    var newKings   = state.kings
    var newMailbox = state.mailbox - from

    if (isWhite) newWhite ^= fromToBB else newBlack ^= fromToBB

    def clr(bb: Bitboard, sqBB: Bitboard): Bitboard = bb & ~sqBB

    def clearPiece(pt: PieceType, sqBB: Bitboard): Unit = pt match {
      case PieceType.Pawn   => newPawns = clr(newPawns, sqBB)
      case PieceType.Knight => newKnights = clr(newKnights, sqBB)
      case PieceType.Bishop => newBishops = clr(newBishops, sqBB)
      case PieceType.Rook   => newRooks = clr(newRooks, sqBB)
      case PieceType.Queen  => newQueens = clr(newQueens, sqBB)
      case PieceType.King   => newKings = clr(newKings, sqBB)
      case _                => ()
    }

    def setPiece(pt: PieceType, sqBB: Bitboard): Unit = pt match {
      case PieceType.Pawn   => newPawns |= sqBB
      case PieceType.Knight => newKnights |= sqBB
      case PieceType.Bishop => newBishops |= sqBB
      case PieceType.Rook   => newRooks |= sqBB
      case PieceType.Queen  => newQueens |= sqBB
      case PieceType.King   => newKings |= sqBB
      case _                => ()
    }

    clearPiece(movingPiece.pieceType, fromBB)

    val targetPiece = if (mv.isCapture && mv.flags != Move.EnPassantCapture) state.mailbox.get(to) else None
    targetPiece.foreach { p =>
      if (isWhite) newBlack = clr(newBlack, toBB) else newWhite = clr(newWhite, toBB)
      clearPiece(p.pieceType, toBB)
    }

    def moveCastlingRook(rookFrom: Square, rookTo: Square, color: Color, isWhite: Boolean): Unit = {
      val rookBB = Bitboard.fromSquare(rookFrom) | Bitboard.fromSquare(rookTo)
      if (isWhite) newWhite ^= rookBB else newBlack ^= rookBB
      newRooks ^= rookBB
      newMailbox -= rookFrom
      newMailbox += (rookTo -> Piece(color, PieceType.Rook))
    }

    var newEnPassant: Option[Square] = None
    val rankOffset                   = 8 // squares per rank

    mv.flags match {
      case Move.DoublePawnPush =>
        newPawns |= toBB
        newMailbox += (to -> Piece(color, PieceType.Pawn))
        val epIdx = if (isWhite) to.index - rankOffset else to.index + rankOffset
        newEnPassant = Some(Square.fromIndex(epIdx))

      case Move.EnPassantCapture =>
        val victimIdx = if (isWhite) to.index - rankOffset else to.index + rankOffset
        val victimBB  = Bitboard.fromSquare(Square.fromIndex(victimIdx))
        if (isWhite) newBlack = clr(newBlack, victimBB) else newWhite = clr(newWhite, victimBB)
        newPawns = clr(newPawns, victimBB)
        newPawns |= toBB
        newMailbox -= Square.fromIndex(victimIdx)
        newMailbox += (to -> Piece(color, PieceType.Pawn))

      case Move.KingCastle =>
        newKings |= toBB
        newMailbox += (to -> Piece(color, PieceType.King))
        val (rookFrom, rookTo) = if (isWhite) (Square('h', 1), Square('f', 1)) else (Square('h', 8), Square('f', 8))
        moveCastlingRook(rookFrom, rookTo, color, isWhite)

      case Move.QueenCastle =>
        newKings |= toBB
        newMailbox += (to -> Piece(color, PieceType.King))
        val (rookFrom, rookTo) = if (isWhite) (Square('a', 1), Square('d', 1)) else (Square('a', 8), Square('d', 8))
        moveCastlingRook(rookFrom, rookTo, color, isWhite)

      case _ if mv.isPromotion =>
        val promType = Position.promotionPieceType(mv.flags)
        setPiece(promType, toBB)
        newMailbox += (to -> Piece(color, promType))

      case _ =>
        setPiece(movingPiece.pieceType, toBB)
        newMailbox += (to -> Piece(color, movingPiece.pieceType))
    }

    val newCastlingRights = Position.updatedCastlingRights(
      state.castlingRights,
      movingPiece,
      from,
      targetPiece,
      to,
      isWhite
    )

    state.copy(
      whitePieces = newWhite,
      blackPieces = newBlack,
      pawns = newPawns,
      knights = newKnights,
      bishops = newBishops,
      rooks = newRooks,
      queens = newQueens,
      kings = newKings,
      mailbox = newMailbox,
      activeColor = state.activeColor.opponent,
      enPassant = newEnPassant,
      castlingRights = newCastlingRights,
      halfMoveClock = if (movingPiece.pieceType == PieceType.Pawn || mv.isCapture) 0 else state.halfMoveClock + 1,
      fullMoveNumber = if (state.activeColor.isBlack) state.fullMoveNumber + 1 else state.fullMoveNumber
    )
  }
