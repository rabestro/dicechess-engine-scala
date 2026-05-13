package dicechess.engine.domain

import scala.util.{Try, Success, Failure}
import scala.util.boundary
import scala.util.boundary.break

/** Bidirectional Forsyth-Edwards Notation (FEN) parser.
  *
  * Translates standard FEN strings directly into the engine's hybrid architecture (8 Bitboards + Mailbox map) and vice
  * versa.
  */
object FenParser:

  /** The standard starting position in FEN notation. */
  val InitialPosition: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  /** Parses a FEN string into a [[GameState]]. */
  def parse(fen: String): Either[String, GameState] =
    val parts = fen.trim.split("\\s+")
    if parts.length != 6 then Left(s"Invalid FEN: Expected 6 parts, found $${parts.length}")
    else
      val boardPart   = parts(0)
      val colorPart   = parts(1)
      val castling    = parts(2)
      val enPassant   = parts(3)
      val halfMoveStr = parts(4)
      val fullMoveStr = parts(5)

      for
        stateTuple <- parseBoard(boardPart)
        (white, black, pawns, knights, bishops, rooks, queens, kings, mailbox) = stateTuple
        activeColor <- parseColor(colorPart)
        epSquare    <- parseEnPassant(enPassant)
        halfMove    <- parseInt(halfMoveStr, "half-move clock")
        fullMove    <- parseInt(fullMoveStr, "full-move number")
      yield GameState(
        whitePieces = white,
        blackPieces = black,
        pawns = pawns,
        knights = knights,
        bishops = bishops,
        rooks = rooks,
        queens = queens,
        kings = kings,
        mailbox = mailbox,
        activeColor = activeColor,
        castlingRights = castling,
        enPassant = epSquare,
        halfMoveClock = halfMove,
        fullMoveNumber = fullMove
      )

  private def parseBoard(boardStr: String): Either[
    String,
    (Bitboard, Bitboard, Bitboard, Bitboard, Bitboard, Bitboard, Bitboard, Bitboard, Map[Square, Piece])
  ] = boundary:
    val ranks = boardStr.split("/")
    if ranks.length != 8 then break(Left(s"Invalid FEN board: Expected 8 ranks, found $${ranks.length}"))

    var white   = Bitboard.empty
    var black   = Bitboard.empty
    var pawns   = Bitboard.empty
    var knights = Bitboard.empty
    var bishops = Bitboard.empty
    var rooks   = Bitboard.empty
    var queens  = Bitboard.empty
    var kings   = Bitboard.empty
    var mailbox = Map.empty[Square, Piece]

    for (rankIndex <- 0 until 8) do
      val rankStr    = ranks(rankIndex)
      val actualRank = 8 - rankIndex // Rank 8 is at index 0
      var fileChar   = 'a'

      for char <- rankStr do
        if char.isDigit then fileChar = (fileChar + char.asDigit).toChar
        else
          val maybePiece = char match
            case 'P' => Some(Piece(Color.White, PieceType.Pawn))
            case 'N' => Some(Piece(Color.White, PieceType.Knight))
            case 'B' => Some(Piece(Color.White, PieceType.Bishop))
            case 'R' => Some(Piece(Color.White, PieceType.Rook))
            case 'Q' => Some(Piece(Color.White, PieceType.Queen))
            case 'K' => Some(Piece(Color.White, PieceType.King))
            case 'p' => Some(Piece(Color.Black, PieceType.Pawn))
            case 'n' => Some(Piece(Color.Black, PieceType.Knight))
            case 'b' => Some(Piece(Color.Black, PieceType.Bishop))
            case 'r' => Some(Piece(Color.Black, PieceType.Rook))
            case 'q' => Some(Piece(Color.Black, PieceType.Queen))
            case 'k' => Some(Piece(Color.Black, PieceType.King))
            case _   => None

          maybePiece match
            case Some(piece) =>
              if fileChar > 'h' then break(Left(s"Invalid FEN board: Rank $actualRank overflows 8 files"))
              val sq = Square(fileChar, actualRank)
              mailbox = mailbox.updated(sq, piece)

              if piece.color.isWhite then white = white.add(sq) else black = black.add(sq)

              piece.pieceType match
                case PieceType.Pawn   => pawns = pawns.add(sq)
                case PieceType.Knight => knights = knights.add(sq)
                case PieceType.Bishop => bishops = bishops.add(sq)
                case PieceType.Rook   => rooks = rooks.add(sq)
                case PieceType.Queen  => queens = queens.add(sq)
                case PieceType.King   => kings = kings.add(sq)

              fileChar = (fileChar + 1).toChar
            case None =>
              break(Left(s"Invalid FEN board: Unknown piece character '$char'"))

      if fileChar != 'i' then break(Left(s"Invalid FEN board: Rank $actualRank does not have exactly 8 files"))

    Right((white, black, pawns, knights, bishops, rooks, queens, kings, mailbox))

  private def parseColor(c: String): Either[String, Color] = c match
    case "w" => Right(Color.White)
    case "b" => Right(Color.Black)
    case _   => Left(s"Invalid FEN active color: Expected 'w' or 'b', got '$c'")

  private def parseEnPassant(ep: String): Either[String, Option[Square]] =
    if ep == "-" then Right(None)
    else Square.fromNotation(ep).map(Some(_)).toRight(s"Invalid FEN en passant square: '$ep'")

  private def parseInt(s: String, name: String): Either[String, Int] =
    Try(s.toInt) match
      case Success(v) => Right(v)
      case Failure(_) => Left(s"Invalid FEN $name: '$s' is not an integer")

  /** Serializes a [[GameState]] back into a standard FEN string. */
  def serialize(state: GameState): String =
    val boardStr = serializeBoard(state.mailbox)
    val colorStr = if state.activeColor.isWhite then "w" else "b"
    val epStr    = state.enPassant.map(_.toNotation).getOrElse("-")

    s"$boardStr $colorStr ${state.castlingRights} $epStr ${state.halfMoveClock} ${state.fullMoveNumber}"

  private def serializeBoard(mailbox: Map[Square, Piece]): String =
    val ranks = for rank <- (1 to 8).reverse yield
      var emptyCount  = 0
      val rankBuilder = new StringBuilder

      for file <- 'a' to 'h' do
        val sq = Square(file, rank)
        mailbox.get(sq) match
          case None        => emptyCount += 1
          case Some(piece) =>
            if emptyCount > 0 then
              rankBuilder.append(emptyCount)
              emptyCount = 0
            rankBuilder.append(pieceToChar(piece))

      if emptyCount > 0 then rankBuilder.append(emptyCount)
      rankBuilder.toString

    ranks.mkString("/")

  private def pieceToChar(piece: Piece): Char =
    val char = piece.pieceType match
      case PieceType.Pawn   => 'P'
      case PieceType.Knight => 'N'
      case PieceType.Bishop => 'B'
      case PieceType.Rook   => 'R'
      case PieceType.Queen  => 'Q'
      case PieceType.King   => 'K'
    if piece.color.isWhite then char else char.toLower
