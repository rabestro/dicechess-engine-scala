package dicechess.engine.domain

/** Board-symmetry transforms and position canonicalization for Dice Chess.
  *
  * Two positions related by a rules-preserving symmetry have identical win probability, so collapsing them to a single
  * canonical representative lets downstream consumers pool statistics (analytics) and share results across equivalent
  * positions (search transposition tables, Monte-Carlo caches).
  *
  * This object provides **color-flip** only — the always-legal symmetry. Horizontal mirror (legal only without castling
  * rights) is a separate step. Horizontal *shift* is intentionally excluded: translation is not an isometry of the
  * bounded 8x8 board (it changes a piece's distance to the edge, and therefore its mobility), so it does **not**
  * preserve win probability.
  *
  * ## Color-flip
  *
  * A vertical board mirror (rank `r` <-> rank `9 - r`) combined with swapping every piece's colour and the side to
  * move. Because Black's downward pawns become White's upward pawns, the result is a strategically identical position
  * with the side to move relabelled (white-to-move <-> black-to-move). It is an **involution**:
  * `colorFlip(colorFlip(s)) == s`.
  */
object Symmetry:

  /** Vertically mirrors a [[Bitboard]] (rank `r` <-> rank `9 - r`).
    *
    * In LERF mapping each rank occupies one byte of the underlying `Long`, so reversing the eight bytes reverses the
    * ranks while keeping files intact.
    */
  private inline def flipVertical(bb: Bitboard): Bitboard =
    Bitboard(java.lang.Long.reverseBytes(bb.value))

  /** Returns the color-flipped position: a vertical mirror combined with a colour and side-to-move swap.
    *
    * Castling rights are swapped between colours while keeping their side (White king-side <-> Black king-side). The
    * en-passant target squares are mirrored vertically; their files — and therefore the [[GameFlags]] file mask — are
    * unchanged. Dice pool, half-move clock and full-move number are preserved, which makes the transform an exact
    * involution.
    */
  def colorFlip(state: GameState): GameState =
    val mailbox = new Array[Piece](64)
    var i       = 0
    while i < 64 do
      val src = state.mailbox(Square.fromIndex(i ^ 56))
      mailbox(i) = if src.isEmpty then Piece.Empty else Piece(src.color.opponent, src.pieceType)
      i += 1

    // Castling bits are K=0, Q=1, k=2, q=3 — swap the White (0-1) and Black (2-3) pairs, keeping king/queen side.
    val cr        = state.flags.castlingRights
    val swappedCr = ((cr & 0x3) << 2) | ((cr & 0xc) >>> 2)

    val flippedFlags = GameFlags.fromList(
      color = state.activeColor.opponent,
      castlingRights = swappedCr,
      enPassantFiles = state.flags.enPassantFiles,
      dicePool = state.flags.dicePool,
      halfMoveClock = state.flags.halfMoveClock
    )

    state.copy(
      whitePieces = flipVertical(state.blackPieces),
      blackPieces = flipVertical(state.whitePieces),
      pawns = flipVertical(state.pawns),
      knights = flipVertical(state.knights),
      bishops = flipVertical(state.bishops),
      rooks = flipVertical(state.rooks),
      queens = flipVertical(state.queens),
      kings = flipVertical(state.kings),
      mailbox = Mailbox.fromBuilder(mailbox),
      flags = flippedFlags,
      enPassant = flipVertical(state.enPassant)
    )

  /** Canonical representative of a position's color-flip class: always **white to move**.
    *
    * A black-to-move position is color-flipped to its white-to-move equivalent; a white-to-move position is returned
    * unchanged. The transform preserves the side-to-move win probability, so a position and its flip may be pooled
    * under this single representative. Idempotent: `canonical(canonical(s)) == canonical(s)`.
    */
  def canonical(state: GameState): GameState =
    if state.activeColor.isBlack then colorFlip(state) else state

  /** Stable canonical key for a position's color-flip class: the DFEN of [[canonical]].
    *
    * Two color-flip-equivalent positions produce the same key. The key retains the half-move/full-move counters and
    * dice pool from the DFEN; callers that need a counter-independent identity (e.g. statistics dedup) should normalise
    * those fields themselves.
    */
  def canonicalKey(state: GameState): String =
    FenParser.serialize(canonical(state))
