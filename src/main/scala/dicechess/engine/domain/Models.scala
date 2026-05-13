package dicechess.engine.domain

// ==============================================================================
// 1. Color Opaque Type
// ==============================================================================

/** Represents the player colors: White (0) or Black (1).
  */
opaque type Color = Int

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

// ==============================================================================
// 2. PieceType Opaque Type
// ==============================================================================

/** Represents chess piece types corresponding to dice values: 1: Pawn, 2: Knight, 3: Bishop, 4: Rook, 5: Queen, 6: King
  */
opaque type PieceType = Int

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

// ==============================================================================
// 3. Piece Opaque Type (Packed)
// ==============================================================================

/** Packs both Color and PieceType into a single primitive Int. Layout: [Color bit | PieceType bits 0-2]
  */
opaque type Piece = Int

object Piece:
  def apply(color: Color, pieceType: PieceType): Piece =
    (color << 3) | pieceType

  extension (piece: Piece)
    inline def color: Color         = piece >>> 3
    inline def pieceType: PieceType = piece & 7

// ==============================================================================
// 4. Square Opaque Type
// ==============================================================================

/** Represents an 8x8 chess board index from 0 (a1) to 63 (h8).
  */
opaque type Square = Int

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

// ==============================================================================
// 5. MicroMove Opaque Type (Packed)
// ==============================================================================

/** Packs a micro-move into 16 bits: [Promotion 4 bits | To-Square 6 bits | From-Square 6 bits]
  */
opaque type MicroMove = Int

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

// ==============================================================================
// 6. Composite Structures (Domain aggregates)
// ==============================================================================

/** Represents a full turn: 1 dice roll + up to 3 micro-moves.
  */
case class Turn(diceRoll: Int, microMoves: List[MicroMove]):
  require(microMoves.nonEmpty, "Turn must contain at least one micro-move")
  require(microMoves.length <= 3, "Turn cannot contain more than 3 micro-moves")

/** Represents total game state meta-information.
  */
case class GameState(
    board: Map[Square, Piece],
    activeColor: Color,
    castlingRights: String,
    enPassant: Option[Square],
    halfMoveClock: Int,
    fullMoveNumber: Int
)
