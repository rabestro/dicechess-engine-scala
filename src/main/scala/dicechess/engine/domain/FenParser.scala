package dicechess.engine.domain

import scala.collection.mutable

/** Parser for Forsyth-Edwards Notation (FEN) tailored for Dice Chess.
  */
object FenParser {

  val InitialPosition: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  def parse(fen: String): Either[String, GameState] = {
    try {
      val parts = fen.split(" ")
      if (parts.length < 4) return Left("Invalid FEN: insufficient parts")

      val board       = parts(0)
      val activeColor = if (parts(1) == "w") Color.White else Color.Black
      val castling    = parts(2)
      val enPassant   = if (parts(3) == "-") None else Square.fromNotation(parts(3))
      val halfMove    = if (parts.length > 4) parts(4).toInt else 0
      val fullMove    = if (parts.length > 5) parts(5).toInt else 1

      val ranks = board.split("/")
      if (ranks.length != 8) return Left(s"Invalid FEN: board must have 8 ranks, found ${ranks.length}")

      var whitePieces = Bitboard.empty
      var blackPieces = Bitboard.empty
      var pawns       = Bitboard.empty
      var knights     = Bitboard.empty
      var bishops     = Bitboard.empty
      var rooks       = Bitboard.empty
      var queens      = Bitboard.empty
      var kings       = Bitboard.empty
      val mailbox     = mutable.Map.empty[Square, Piece]

      var r = 0
      while (r < ranks.length) {
        val rankStr   = ranks(r)
        val rankIndex = 7 - r
        var file      = 0
        var i         = 0
        while (i < rankStr.length) {
          val char = rankStr.charAt(i)
          if (char.isDigit) {
            file += char.asDigit
          } else {
            if (file >= 8) return Left(s"Rank $rankIndex overflows 8 files")
            val sq    = Square.fromIndex(rankIndex * 8 + file)
            val color = if (char.isUpper) Color.White else Color.Black
            val pt    = char.toLower match {
              case 'p' => PieceType.Pawn
              case 'n' => PieceType.Knight
              case 'b' => PieceType.Bishop
              case 'r' => PieceType.Rook
              case 'q' => PieceType.Queen
              case 'k' => PieceType.King
              case _   => return Left(s"Unknown piece character '$char'")
            }

            val piece = Piece(color, pt)
            mailbox.put(sq, piece)

            val bb = Bitboard.fromSquare(sq)
            if (color.isWhite) whitePieces |= bb else blackPieces |= bb

            pt match {
              case PieceType.Pawn   => pawns |= bb
              case PieceType.Knight => knights |= bb
              case PieceType.Bishop => bishops |= bb
              case PieceType.Rook   => rooks |= bb
              case PieceType.Queen  => queens |= bb
              case PieceType.King   => kings |= bb
              case _                => ()
            }

            file += 1
          }
          i += 1
        }
        if (file != 8) return Left(s"Rank $rankIndex must have 8 files, found $file")
        r += 1
      }

      Right(
        GameState(
          whitePieces,
          blackPieces,
          pawns,
          knights,
          bishops,
          rooks,
          queens,
          kings,
          mailbox.toMap,
          activeColor,
          castling,
          enPassant,
          halfMove,
          fullMove
        )
      )
    } catch {
      case e: Exception => Left(s"FEN parsing error: ${e.getMessage}")
    }
  }

  def serialize(state: GameState): String = {
    val res = new StringBuilder()
    var r   = 7
    while (r >= 0) {
      var empty = 0
      var f     = 0
      while (f < 8) {
        val sq = Square.fromIndex(r * 8 + f)
        state.mailbox.get(sq) match {
          case Some(piece) =>
            if (empty > 0) res.append(empty.toString)
            empty = 0
            val char = piece.pieceType.asNotation
            res.append(if (piece.color.isWhite) char.toUpperCase else char.toLowerCase)
          case None =>
            empty += 1
        }
        f += 1
      }
      if (empty > 0) res.append(empty.toString)
      if (r > 0) res.append("/")
      r -= 1
    }

    res.append(" ")
    res.append(if (state.activeColor.isWhite) "w" else "b")
    res.append(" ")
    res.append(state.castlingRights)
    res.append(" ")
    res.append(state.enPassant.map(_.toNotation).getOrElse("-"))
    res.append(" ")
    res.append(state.halfMoveClock.toString)
    res.append(" ")
    res.append(state.fullMoveNumber.toString)

    res.toString
  }
}
