package dicechess.engine.domain

opaque type Color = Int

/** Represents the player colors: White (0) or Black (1).
  *
  * Uses bitwise XOR for fast toggling between opponents.
  */
object Color:
  val White: Color = 0
  val Black: Color = 1

  /** Safe builder to validate boundary invariants.
    */
  def apply(value: Int): Color =
    require(value == 0 || value == 1, s"Invalid color ID: $value")
    value

  extension (color: Color)
    inline def opponent: Color  = color ^ 1
    inline def isWhite: Boolean = color == Color.White
    inline def isBlack: Boolean = color == Color.Black
    inline def value: Int       = color

opaque type PieceType = Int

/** Represents chess piece types corresponding to dice values.
  *
  * Values are 1: Pawn, 2: Knight, 3: Bishop, 4: Rook, 5: Queen, 6: King.
  */
object PieceType:
  val Pawn: PieceType   = 1
  val Knight: PieceType = 2
  val Bishop: PieceType = 3
  val Rook: PieceType   = 4
  val Queen: PieceType  = 5
  val King: PieceType   = 6

  val all: List[PieceType] = List(Pawn, Knight, Bishop, Rook, Queen, King)

  def fromDice(value: Int): Option[PieceType] =
    if value >= 1 && value <= 6 then Some(value) else None

  extension (pt: PieceType)
    inline def diceValue: Int = pt
    def asNotation: String    = pt match
      case PieceType.Pawn   => "p"
      case PieceType.Knight => "n"
      case PieceType.Bishop => "b"
      case PieceType.Rook   => "r"
      case PieceType.Queen  => "q"
      case PieceType.King   => "k"

opaque type Piece = Int

/** A packed chess piece combining [[Color]] and [[PieceType]].
  *
  * Memory Layout (4 bits total):
  *   - Bit 3: Color (0 for White, 1 for Black)
  *   - Bits 0-2: PieceType (1-6)
  */
object Piece:
  def apply(color: Color, pieceType: PieceType): Piece =
    (color << 3) | pieceType

  extension (piece: Piece)
    inline def color: Color         = piece >>> 3
    inline def pieceType: PieceType = piece & 7

opaque type Square = Int

/** Represents a chess board square index.
  *
  * The index ranges from 0 (a1) to 63 (h8), mapped row by row (a1, b1... h8).
  */
object Square:
  /** Builds a square from coordinate syntax.
    */
  def apply(file: Char, rank: Int): Square =
    require(file >= 'a' && file <= 'h', s"Invalid file: $file. Must be 'a'-'h'.")
    require(rank >= 1 && rank <= 8, s"Invalid rank: $rank. Must be 1-8.")
    ((rank - 1) * 8) + (file - 'a')

  /** Directly injects a pre-validated raw index.
    */
  def fromIndex(idx: Int): Square =
    require(idx >= 0 && idx < 64, s"Invalid square index: $idx")
    idx

  def fromNotation(notation: String): Option[Square] =
    if notation.length == 2 then
      val file     = notation.charAt(0)
      val rankChar = notation.charAt(1)
      if file >= 'a' && file <= 'h' && rankChar >= '1' && rankChar <= '8' then Some(Square(file, rankChar.asDigit))
      else None
    else None

  extension (sq: Square)
    inline def file: Char         = ((sq % 8) + 'a').toChar
    inline def rank: Int          = (sq / 8) + 1
    inline def toNotation: String = s"$file$rank"
    inline def index: Int         = sq

opaque type MicroMove = Int

/** A high-performance 16-bit packed micro-move.
  *
  * Memory Layout:
  *   - Bits 12-15: Promotion PieceType (optional, 0 if none)
  *   - Bits 6-11: Target [[Square]] (0-63)
  *   - Bits 0-5: Origin [[Square]] (0-63)
  */
object MicroMove:
  def apply(from: Square, to: Square, promotion: Option[PieceType] = None): MicroMove =
    val promValue = promotion.getOrElse(0) // 0 indicates no promotion
    (promValue << 12) | (to << 6) | from

  extension (mv: MicroMove)
    inline def from: Square          = mv & 0x3f
    inline def to: Square            = (mv >>> 6) & 0x3f
    def promotion: Option[PieceType] =
      val prom = (mv >>> 12) & 0x0f
      if prom == 0 then None else Some(prom)

    def toNotation: String =
      import PieceType.asNotation
      val promStr = promotion.map(_.asNotation).getOrElse("")
      s"${Square.toNotation(from)}${Square.toNotation(to)}$promStr"

opaque type Bitboard = Long

/** A 64-bit integer representing a set of squares on the chess board.
  *
  * Uses Little-Endian Rank-File (LERF) mapping, where bit 0 is a1 and bit 63 is h8.
  */
object Bitboard:
  /** An empty bitboard (no squares occupied). */
  val empty: Bitboard = 0L

  /** A full bitboard (all squares occupied). */
  val full: Bitboard = -1L

  /** Creates a bitboard with a single square occupied. */
  def fromSquare(sq: Square): Bitboard = 1L << Square.index(sq)

  extension (bb: Bitboard)
    /** Bitwise AND (Intersection) */
    inline infix def &(other: Bitboard): Bitboard = bb & other

    /** Bitwise OR (Union) */
    inline infix def |(other: Bitboard): Bitboard = bb | other

    /** Bitwise XOR (Symmetric Difference) */
    inline infix def ^(other: Bitboard): Bitboard = bb ^ other

    /** Bitwise NOT (Complement) */
    inline def unary_~ : Bitboard = ~bb

    /** Adds a square to the bitboard. */
    inline def add(sq: Square): Bitboard = bb | (1L << Square.index(sq))

    /** Removes a square from the bitboard. */
    inline def remove(sq: Square): Bitboard = bb & ~(1L << Square.index(sq))

    /** Checks if a square is occupied on this bitboard. */
    inline def contains(sq: Square): Boolean = (bb & (1L << Square.index(sq))) != 0L

    /** Returns the number of occupied squares (using JVM POPCNT intrinsic). */
    inline def count: Int = java.lang.Long.bitCount(bb)

    /** Returns true if the bitboard has no occupied squares. */
    inline def isEmpty: Boolean = bb == 0L

    /** Exposes the underlying Long value. */
    inline def value: Long = bb

/** A chess turn consisting of a dice outcome and a sequence of micro-moves.
  *
  * @param diceRoll
  *   The result of the dice (1-6).
  * @param microMoves
  *   The list of 1 to 3 moves executed within this turn.
  */
case class Turn(diceRoll: Int, microMoves: List[MicroMove]):
  require(microMoves.nonEmpty, "Turn must contain at least one micro-move")
  require(microMoves.length <= 3, "Turn cannot contain more than 3 micro-moves")

/** The complete snapshot of a Dice Chess game state.
  *
  * @param board
  *   Map of occupied squares to their respective pieces.
  * @param activeColor
  *   The color of the player whose turn it is.
  * @param castlingRights
  *   FEN-standard castling string (e.g., "KQkq").
  * @param enPassant
  *   Potential en passant target square.
  * @param halfMoveClock
  *   Clock for the 50-move rule.
  * @param fullMoveNumber
  *   The number of the current full move.
  */
case class GameState(
    board: Map[Square, Piece],
    activeColor: Color,
    castlingRights: String,
    enPassant: Option[Square],
    halfMoveClock: Int,
    fullMoveNumber: Int
)
