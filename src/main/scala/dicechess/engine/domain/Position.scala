package dicechess.engine.domain

import scala.annotation.targetName

/** Internal helpers for applying moves to a [[GameState]].
  *
  * Encapsulates castling-rights bookkeeping and the mutable bitboard scratch-pad used during move application.
  * Intentionally `private` — all external callers go through the `makeMove` extension methods on [[GameState]].
  */
private object Position:

  /** Maps a promotion [[Move]] flag to the target [[PieceType]].
    *
    * Defaults to `Queen` for any flag not explicitly listed (quiet move, double push, etc. will never reach this path
    * in practice).
    *
    * @param flags
    *   the 4-bit flag field from a [[Move]]
    * @return
    *   the piece type the pawn should be promoted to
    */
  def promotionPieceType(flags: Int): PieceType = flags match {
    case Move.KnightPromotion | Move.KnightPromoCapture => PieceType.Knight
    case Move.BishopPromotion | Move.BishopPromoCapture => PieceType.Bishop
    case Move.RookPromotion | Move.RookPromoCapture     => PieceType.Rook
    case _                                              => PieceType.Queen
  }

  /** Computes the new castling-rights string after a move.
    *
    * Applies two independent revocations in sequence:
    *
    *   1. **Captured rook** — if a rook is taken on its home corner, the corresponding right is removed.
    *   2. **Moving piece** — a king move removes both rights for its color; a rook move from its home square removes
    *      only the matching right.
    *
    * Returns `"-"` when no rights remain (FEN convention).
    *
    * @param rights
    *   current FEN castling-rights string (e.g. `"KQkq"`)
    * @param movingPiece
    *   the piece that is moving
    * @param from
    *   origin square of the move
    * @param targetPiece
    *   the captured piece, if any
    * @param to
    *   destination square of the move
    * @param isWhite
    *   `true` if the moving side is White
    * @return
    *   updated castling-rights string, or `"-"` if none remain
    */
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

  /** Removes castling rights caused by the moving piece.
    *
    * A king move revokes both rights for its color. A rook move from a home corner revokes only the right for that
    * corner; any other piece leaves rights unchanged.
    */
  private def removeMoverRights(rights: String, pieceType: PieceType, from: Square, isWhite: Boolean): String =
    pieceType match
      case PieceType.King =>
        if isWhite then rights.filterNot(c => c == 'K' || c == 'Q')
        else rights.filterNot(c => c == 'k' || c == 'q')
      case PieceType.Rook => removeRookMoverRights(rights, from, isWhite)
      case _              => rights

  /** Removes the castling right associated with a rook that has moved off its home square.
    *
    * Only corner squares (a1, h1 for White; a8, h8 for Black) trigger a revocation; all other origins are no-ops.
    */
  private def removeRookMoverRights(rights: String, from: Square, isWhite: Boolean): String =
    if isWhite then
      if from == Square('a', 1) then rights.filterNot(_ == 'Q')
      else if from == Square('h', 1) then rights.filterNot(_ == 'K')
      else rights
    else if from == Square('a', 8) then rights.filterNot(_ == 'q')
    else if from == Square('h', 8) then rights.filterNot(_ == 'k')
    else rights

  /** Removes castling rights caused by a rook being captured on its home corner.
    *
    * Only applies when the captured piece is a `Rook` and the destination square is one of the four corner squares.
    */
  private def removeCapturedRookRights(rights: String, targetPiece: Option[Piece], to: Square): String =
    targetPiece match
      case Some(p) if p.pieceType == PieceType.Rook =>
        if to == Square('a', 8) then rights.filterNot(_ == 'q')
        else if to == Square('h', 8) then rights.filterNot(_ == 'k')
        else if to == Square('a', 1) then rights.filterNot(_ == 'Q')
        else if to == Square('h', 1) then rights.filterNot(_ == 'K')
        else rights
      case _ => rights

  /** Mutable scratch-pad that mirrors a [[GameState]]'s bitboards during move application.
    *
    * Created at the start of each `makeMove` call and discarded once the new [[GameState]] is constructed via
    * `state.copy(...)`. Using an explicit class rather than local `def` closures keeps cognitive complexity low and
    * groups all board-mutation operations in one place.
    *
    * @param state
    *   the position from which initial bitboard values are copied
    */
  class BitboardMutator(state: GameState):
    var white: Bitboard             = state.whitePieces
    var black: Bitboard             = state.blackPieces
    var pawns: Bitboard             = state.pawns
    var knights: Bitboard           = state.knights
    var bishops: Bitboard           = state.bishops
    var rooks: Bitboard             = state.rooks
    var queens: Bitboard            = state.queens
    var kings: Bitboard             = state.kings
    var mailbox: Map[Square, Piece] = state.mailbox

    /** Clears the bit for `sqBB` from the type-specific bitboard of `pt`. */
    def clearPiece(pt: PieceType, sqBB: Bitboard): Unit = pt match
      case PieceType.Pawn   => pawns = pawns & ~sqBB
      case PieceType.Knight => knights = knights & ~sqBB
      case PieceType.Bishop => bishops = bishops & ~sqBB
      case PieceType.Rook   => rooks = rooks & ~sqBB
      case PieceType.Queen  => queens = queens & ~sqBB
      case PieceType.King   => kings = kings & ~sqBB
      case _                => ()

    /** Sets the bit for `sqBB` in the type-specific bitboard of `pt`. */
    def setPiece(pt: PieceType, sqBB: Bitboard): Unit = pt match
      case PieceType.Pawn   => pawns |= sqBB
      case PieceType.Knight => knights |= sqBB
      case PieceType.Bishop => bishops |= sqBB
      case PieceType.Rook   => rooks |= sqBB
      case PieceType.Queen  => queens |= sqBB
      case PieceType.King   => kings |= sqBB
      case _                => ()

    /** Toggles `fromToBB` in the appropriate color bitboard (XOR clears origin, sets destination). */
    def moveColor(isWhite: Boolean, fromToBB: Bitboard): Unit =
      if isWhite then white ^= fromToBB else black ^= fromToBB

    /** Removes `sqBB` from the opponent's color bitboard (used when a piece is captured). */
    def removeCaptured(isWhite: Boolean, sqBB: Bitboard): Unit =
      if isWhite then black = black & ~sqBB else white = white & ~sqBB

    /** Moves the castling rook: updates the color bitboard, the rooks bitboard, and the mailbox atomically.
      *
      * @param isWhite
      *   `true` if the rook belongs to White
      * @param rookFrom
      *   home square of the rook (e.g. h1 for White king-side)
      * @param rookTo
      *   destination square of the rook (e.g. f1 for White king-side)
      * @param color
      *   color of the rook (used to populate the mailbox entry)
      */
    def moveRook(isWhite: Boolean, rookFrom: Square, rookTo: Square, color: Color): Unit =
      val rookBB = Bitboard.fromSquare(rookFrom) | Bitboard.fromSquare(rookTo)
      if isWhite then white ^= rookBB else black ^= rookBB
      rooks ^= rookBB
      mailbox = mailbox - rookFrom + (rookTo -> Piece(color, PieceType.Rook))

extension (state: GameState)
  /** Applies a [[MicroMove]] (turn-based) to the current game state.
    *
    * Handles piece movement, captures, and basic bitboard updates. This is a _raw_ move application — higher-level
    * logic such as switching turns after three micro-moves is the responsibility of the Turn manager.
    *
    * The half-move clock is reset to 0 on pawn moves and captures; otherwise it is increased by 1.
    *
    * @param mv
    *   the micro-move to apply
    * @return
    *   a new [[GameState]] reflecting the position after the move
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

  /** Applies a [[Move]] (search-optimized) to the current game state.
    *
    * Handles all standard chess rules encoded in the move's 4-bit flag field:
    *
    *   - **Double pawn push** — sets the en-passant square on the passed square.
    *   - **En-passant capture** — removes the captured pawn from the rank behind the destination.
    *   - **King-side / queen-side castling** — moves both king and rook atomically.
    *   - **Promotions** — replaces the pawn with the promoted piece type.
    *   - **Quiet / capture** — standard piece relocation.
    *
    * Side effects on the returned state: active color is flipped, en-passant is cleared (unless a double push just
    * occurred), castling rights are updated, and the half-move / full-move clocks are maintained.
    *
    * @param mv
    *   the move to apply (must be pseudo-legal; legality is enforced by the search layer)
    * @return
    *   a new [[GameState]] reflecting the position after the move
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
