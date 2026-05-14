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
  ): String =
    val r = removeMoverRights(removeCapturedRookRights(rights, targetPiece, to), movingPiece.pieceType, from, isWhite)
    if r.isEmpty then "-" else r

  private def removeMoverRights(rights: String, pieceType: PieceType, from: Square, isWhite: Boolean): String =
    pieceType match
      case PieceType.King =>
        if isWhite then rights.filterNot(c => c == 'K' || c == 'Q')
        else rights.filterNot(c => c == 'k' || c == 'q')
      case PieceType.Rook => removeRookMoverRights(rights, from, isWhite)
      case _              => rights

  private def removeRookMoverRights(rights: String, from: Square, isWhite: Boolean): String =
    if isWhite then
      if from == Square('a', 1) then rights.filterNot(_ == 'Q')
      else if from == Square('h', 1) then rights.filterNot(_ == 'K')
      else rights
    else if from == Square('a', 8) then rights.filterNot(_ == 'q')
    else if from == Square('h', 8) then rights.filterNot(_ == 'k')
    else rights

  private def removeCapturedRookRights(rights: String, targetPiece: Option[Piece], to: Square): String =
    targetPiece match
      case Some(p) if p.pieceType == PieceType.Rook =>
        if to == Square('a', 8) then rights.filterNot(_ == 'q')
        else if to == Square('h', 8) then rights.filterNot(_ == 'k')
        else if to == Square('a', 1) then rights.filterNot(_ == 'Q')
        else if to == Square('h', 1) then rights.filterNot(_ == 'K')
        else rights
      case _ => rights

  /** Mutable working copy of board bitboards for use inside makeMove. */
  class BitboardMutator(state: GameState):
    var white   = state.whitePieces
    var black   = state.blackPieces
    var pawns   = state.pawns
    var knights = state.knights
    var bishops = state.bishops
    var rooks   = state.rooks
    var queens  = state.queens
    var kings   = state.kings
    var mailbox = state.mailbox

    def clearPiece(pt: PieceType, sqBB: Bitboard): Unit = pt match
      case PieceType.Pawn   => pawns = pawns & ~sqBB
      case PieceType.Knight => knights = knights & ~sqBB
      case PieceType.Bishop => bishops = bishops & ~sqBB
      case PieceType.Rook   => rooks = rooks & ~sqBB
      case PieceType.Queen  => queens = queens & ~sqBB
      case PieceType.King   => kings = kings & ~sqBB
      case _                => ()

    def setPiece(pt: PieceType, sqBB: Bitboard): Unit = pt match
      case PieceType.Pawn   => pawns |= sqBB
      case PieceType.Knight => knights |= sqBB
      case PieceType.Bishop => bishops |= sqBB
      case PieceType.Rook   => rooks |= sqBB
      case PieceType.Queen  => queens |= sqBB
      case PieceType.King   => kings |= sqBB
      case _                => ()

    def moveColor(isWhite: Boolean, fromToBB: Bitboard): Unit =
      if isWhite then white ^= fromToBB else black ^= fromToBB

    def removeCaptured(isWhite: Boolean, sqBB: Bitboard): Unit =
      if isWhite then black = black & ~sqBB else white = white & ~sqBB

    def moveRook(isWhite: Boolean, rookFrom: Square, rookTo: Square, color: Color): Unit =
      val rookBB = Bitboard.fromSquare(rookFrom) | Bitboard.fromSquare(rookTo)
      if isWhite then white ^= rookBB else black ^= rookBB
      rooks ^= rookBB
      mailbox = mailbox - rookFrom + (rookTo -> Piece(color, PieceType.Rook))

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
    val isWhite     = movingPiece.color.isWhite
    val fromBB      = Bitboard.fromSquare(from)
    val toBB        = Bitboard.fromSquare(to)
    val b           = new Position.BitboardMutator(state)

    b.mailbox = b.mailbox - from
    b.moveColor(isWhite, fromBB | toBB)
    targetPiece.foreach(_ => b.removeCaptured(isWhite, toBB))
    b.clearPiece(movingPiece.pieceType, fromBB)
    targetPiece.foreach(p => b.clearPiece(p.pieceType, toBB))
    val finalPieceType = mv.promotion.getOrElse(movingPiece.pieceType)
    b.setPiece(finalPieceType, toBB)
    b.mailbox += (to -> Piece(movingPiece.color, finalPieceType))

    state.copy(
      whitePieces = b.white,
      blackPieces = b.black,
      pawns = b.pawns,
      knights = b.knights,
      bishops = b.bishops,
      rooks = b.rooks,
      queens = b.queens,
      kings = b.kings,
      mailbox = b.mailbox,
      halfMoveClock =
        if movingPiece.pieceType == PieceType.Pawn || targetPiece.isDefined then 0 else state.halfMoveClock + 1
    )

  /** Applies a Move (search-optimized) to the current game state.
    *
    * This handles all special chess rules like castling, en passant, and double pawn pushes using the move's flags.
    */
  @targetName("makeMove_Move")
  def makeMove(mv: Move): GameState = {
    val from       = mv.fromSquare
    val to         = mv.toSquare
    val mover      = state.mailbox(from)
    val color      = mover.color
    val isWhite    = color.isWhite
    val fromBB     = Bitboard.fromSquare(from)
    val toBB       = Bitboard.fromSquare(to)
    val rankOffset = if isWhite then -8 else 8
    val b          = new Position.BitboardMutator(state)

    b.mailbox = b.mailbox - from
    b.moveColor(isWhite, fromBB | toBB)
    b.clearPiece(mover.pieceType, fromBB)

    val target = if mv.isCapture && mv.flags != Move.EnPassantCapture then state.mailbox.get(to) else None
    target.foreach { p => b.removeCaptured(isWhite, toBB); b.clearPiece(p.pieceType, toBB) }

    var newEnPassant: Option[Square] = None

    mv.flags match {
      case Move.DoublePawnPush =>
        b.pawns |= toBB
        b.mailbox += (to -> Piece(color, PieceType.Pawn))
        newEnPassant = Some(Square.fromIndex(to.index + rankOffset))

      case Move.EnPassantCapture =>
        val victimBB = Bitboard.fromSquare(Square.fromIndex(to.index + rankOffset))
        b.removeCaptured(isWhite, victimBB)
        b.pawns = (b.pawns & ~victimBB) | toBB
        b.mailbox = b.mailbox - Square.fromIndex(to.index + rankOffset) + (to -> Piece(color, PieceType.Pawn))

      case Move.KingCastle =>
        b.kings |= toBB
        b.mailbox += (to -> Piece(color, PieceType.King))
        val (rFrom, rTo) = if isWhite then (Square('h', 1), Square('f', 1)) else (Square('h', 8), Square('f', 8))
        b.moveRook(isWhite, rFrom, rTo, color)

      case Move.QueenCastle =>
        b.kings |= toBB
        b.mailbox += (to -> Piece(color, PieceType.King))
        val (rFrom, rTo) = if isWhite then (Square('a', 1), Square('d', 1)) else (Square('a', 8), Square('d', 8))
        b.moveRook(isWhite, rFrom, rTo, color)

      case _ if mv.isPromotion =>
        val promType = Position.promotionPieceType(mv.flags)
        b.setPiece(promType, toBB)
        b.mailbox += (to -> Piece(color, promType))

      case _ =>
        b.setPiece(mover.pieceType, toBB)
        b.mailbox += (to -> Piece(color, mover.pieceType))
    }

    state.copy(
      whitePieces = b.white,
      blackPieces = b.black,
      pawns = b.pawns,
      knights = b.knights,
      bishops = b.bishops,
      rooks = b.rooks,
      queens = b.queens,
      kings = b.kings,
      mailbox = b.mailbox,
      activeColor = state.activeColor.opponent,
      enPassant = newEnPassant,
      castlingRights = Position.updatedCastlingRights(state.castlingRights, mover, from, target, to, isWhite),
      halfMoveClock = if mover.pieceType == PieceType.Pawn || mv.isCapture then 0 else state.halfMoveClock + 1,
      fullMoveNumber = if state.activeColor.isBlack then state.fullMoveNumber + 1 else state.fullMoveNumber
    )
  }
