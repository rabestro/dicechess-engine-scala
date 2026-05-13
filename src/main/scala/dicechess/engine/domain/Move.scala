package dicechess.engine.domain

/** A highly optimized 16-bit encoded chess move.
  *
  * Designed to completely avoid garbage collection (GC) during the hot path of the search tree.
  *
  * Memory layout:
  *   - Bits 0-5 (6 bits): Destination square index (to)
  *   - Bits 6-11 (6 bits): Source square index (from)
  *   - Bits 12-15 (4 bits): Move Flags (Quiet, Capture, Promotion, etc.)
  */
opaque type Move = Int

object Move:

  // Bitmasks for extracting move components
  private val ToMask: Int    = 0x3f
  private val FromMask: Int  = 0x3f << 6
  private val FlagsMask: Int = 0x0f << 12

  // Standard 4-bit Move Flags
  val QuietMove: Int        = 0
  val DoublePawnPush: Int   = 1
  val KingCastle: Int       = 2
  val QueenCastle: Int      = 3
  val Capture: Int          = 4
  val EnPassantCapture: Int = 5

  // Promotion flags (Bit 3 is high)
  val KnightPromotion: Int = 8
  val BishopPromotion: Int = 9
  val RookPromotion: Int   = 10
  val QueenPromotion: Int  = 11

  // Promotion Captures (Bits 3 and 2 are high)
  val KnightPromoCapture: Int = 12
  val BishopPromoCapture: Int = 13
  val RookPromoCapture: Int   = 14
  val QueenPromoCapture: Int  = 15

  /** Constructs a new encoded Move from origin, destination, and flags. */
  def apply(from: Square, to: Square, flags: Int): Move =
    (flags << 12) | (Square.index(from) << 6) | Square.index(to)

  /** Constructs a new quiet Move. */
  def apply(from: Square, to: Square): Move =
    apply(from, to, QuietMove)

  /** An empty or uninitialized move (a1 to a1). */
  val empty: Move = 0

  extension (move: Move)
    /** Returns the starting square of the move. */
    def fromSquare: Square = Square.fromIndex((move & FromMask) >> 6)

    /** Returns the destination square of the move. */
    def toSquare: Square = Square.fromIndex(move & ToMask)

    /** Returns the 4-bit flag of the move. */
    def flags: Int = (move & FlagsMask) >> 12

    /** Returns true if the move is any type of capture. */
    def isCapture: Boolean = (flags & 4) != 0

    /** Returns true if the move is any type of promotion. */
    def isPromotion: Boolean = (flags & 8) != 0

    /** Returns true if the move is an en passant capture. */
    def isEnPassant: Boolean = flags == EnPassantCapture

    /** Returns true if the move is a castling move. */
    def isCastling: Boolean = flags == KingCastle || flags == QueenCastle

    /** Returns the raw integer representation of the move. */
    def toInt: Int = move
