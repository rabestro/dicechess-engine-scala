package dicechess.engine.domain

/** Colors of the pieces on the board.
  */
enum Color:
  case White, Black

  def opponent: Color = this match
    case White => Black
    case Black => White

/** Chess piece types corresponding to dice values: 
  * 1: Pawn, 2: Knight, 3: Bishop, 4: Rook, 5: Queen, 6: King
  */
enum PieceType(val diceValue: Int):
  case Pawn   extends PieceType(1)
  case Knight extends PieceType(2)
  case Bishop extends PieceType(3)
  case Rook   extends PieceType(4)
  case Queen  extends PieceType(5)
  case King   extends PieceType(6)

object PieceType:
  def fromDice(value: Int): Option[PieceType] =
    values.find(_.diceValue == value)

/** A chess piece on the board.
  */
case class Piece(color: Color, pieceType: PieceType)

/** A board square representation (e.g. e4, a1).
  */
case class Square(file: Char, rank: Int):
  require(file >= 'a' && file <= 'h', s"Invalid file: $file. Must be between 'a' and 'h'.")
  require(rank >= 1 && rank <= 8, s"Invalid rank: $rank. Must be between 1 and 8.")

  def toNotation: String = s"$file$rank"

object Square:
  def fromNotation(notation: String): Option[Square] =
    if notation.length == 2 then
      val file = notation.charAt(0)
      val rank = notation.charAt(1).asDigit
      if file >= 'a' && file <= 'h' && rank >= 1 && rank <= 8 then
        Some(Square(file, rank))
      else None
    else None

/** Represents a single micro-move (from square, to square). 
  * Dice Chess turns consist of up to 3 micro-moves.
  */
case class MicroMove(from: Square, to: Square, promotion: Option[PieceType] = None):
  def toNotation: String = 
    val promStr = promotion.map(_.toString.take(1).toLowerCase).getOrElse("")
    s"${from.toNotation}${to.toNotation}$promStr"

/** Represents a full turn made by a player: 1 dice roll and up to 3 micro-moves.
  */
case class Turn(diceRoll: Int, microMoves: List[MicroMove]):
  require(microMoves.nonEmpty, "Turn must contain at least one micro-move")
  require(microMoves.length <= 3, "Turn cannot contain more than 3 micro-moves")

/** The state of the Dice Chess board and turn-related meta-information.
  */
case class GameState(
  board: Map[Square, Piece],
  activeColor: Color,
  castlingRights: String, // e.g. "KQkq"
  enPassant: Option[Square],
  halfMoveClock: Int,
  fullMoveNumber: Int
)
