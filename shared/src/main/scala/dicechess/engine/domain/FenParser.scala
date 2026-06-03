package dicechess.engine.domain

import scala.collection.mutable

/** Parser and serialiser for Forsyth-Edwards Notation (FEN).
  *
  * FEN encodes a complete chess position in a single ASCII string consisting of six space-separated fields:
  *
  * ```
  * <piece placement> <active color> <castling rights> <en-passant square> <half-move clock> <full-move number>
  * ```
  *
  * Example (starting position):
  * ```
  * rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
  * ```
  *
  * ## Dice Chess Extension (7th field)
  *
  * This parser also supports an optional **7th field** that stores the current turn's dice pool:
  *
  * ```
  * rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1 pnb
  * ```
  *
  * The 7th field is a string of piece letters (e.g. `"PNB"` for White or `"pnb"` for Black = dice pool `[1, 2, 3]`). It
  * is absent (`"-"`) or omitted when no dice have been rolled yet. [[serialize]] appends it automatically when the pool
  * is non-empty.
  *
  * Both [[parse]] and [[serialize]] are inverses of each other for any valid FEN string.
  */
object FenParser {

  /** FEN string for the standard chess starting position. */
  val InitialPosition: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  /** Parses a FEN string into a [[GameState]].
    *
    * Validates the number of fields, the board layout (exactly 8 ranks, 8 files each), and each piece character. The
    * half-move clock and full-move number default to `0` and `1` respectively when absent (short FEN).
    *
    * @param fen
    *   a FEN string with at least 4 space-separated fields
    * @return
    *   `Right(state)` on success, or `Left(errorMessage)` describing the first parse failure
    */
  def parse(fen: String): Either[String, GameState] = scala.util.boundary {
    try {
      val parts = fen.split(" ")
      if (parts.length < 4) scala.util.boundary.break(Left("Invalid FEN: insufficient parts"))

      val board       = parts(0)
      val activeColor = parts(1) match {
        case "w"   => Color.White
        case "b"   => Color.Black
        case other => scala.util.boundary.break(Left(s"Invalid active color: $other"))
      }
      val castling    = parts(2)
      var castlingInt = 0
      if (castling.contains('K')) castlingInt |= 1
      if (castling.contains('Q')) castlingInt |= 2
      if (castling.contains('k')) castlingInt |= 4
      if (castling.contains('q')) castlingInt |= 8

      val enPassantField = parts(3)
      var epFiles        = 0
      var enPassantBb    = Bitboard.empty
      if (enPassantField != "-") {
        var idx = 0
        while (idx < enPassantField.length) {
          if (idx + 2 <= enPassantField.length) {
            val notation = enPassantField.substring(idx, idx + 2)
            Square.fromNotation(notation) match {
              case Some(sq) =>
                epFiles |= (1 << (sq.file - 'a'))
                enPassantBb = enPassantBb.add(sq)
              case None => scala.util.boundary.break(Left(s"Invalid en-passant notation '$notation'"))
            }
          } else {
            scala.util.boundary.break(Left(s"Invalid en-passant field '$enPassantField'"))
          }
          idx += 2
        }
      }
      val halfMove = if (parts.length > 4) parts(4).toIntOption.getOrElse(0) else 0
      val fullMove = if (parts.length > 5) parts(5).toIntOption.getOrElse(1) else 1

      val dicePool = if (parts.length >= 7) {
        val poolField = parts(6)
        if (poolField == "-") Nil
        else {
          val list = mutable.ListBuffer.empty[Int]
          var idx  = 0
          while (idx < poolField.length) {
            poolField.charAt(idx).toLower match {
              case 'p'   => list += 1
              case 'n'   => list += 2
              case 'b'   => list += 3
              case 'r'   => list += 4
              case 'q'   => list += 5
              case 'k'   => list += 6
              case other => scala.util.boundary.break(Left(s"Invalid dice-pool character '$other'"))
            }
            idx += 1
          }
          list.toList.sorted
        }
      } else {
        Nil
      }

      val flags = GameFlags.fromList(activeColor, castlingInt, epFiles, dicePool, halfMove)

      val ranks = board.split("/")
      if (ranks.length != 8)
        scala.util.boundary.break(Left(s"Invalid FEN: board must have 8 ranks, found ${ranks.length}"))

      var whitePieces = Bitboard.empty
      var blackPieces = Bitboard.empty
      var pawns       = Bitboard.empty
      var knights     = Bitboard.empty
      var bishops     = Bitboard.empty
      var rooks       = Bitboard.empty
      var queens      = Bitboard.empty
      var kings       = Bitboard.empty
      val mailbox     = new Array[Piece](64)

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
            if (file >= 8) scala.util.boundary.break(Left(s"Rank $rankIndex overflows 8 files"))
            val sq    = Square.fromIndex(rankIndex * 8 + file)
            val color = if (char.isUpper) Color.White else Color.Black
            val pt    = char.toLower match {
              case 'p' => PieceType.Pawn
              case 'n' => PieceType.Knight
              case 'b' => PieceType.Bishop
              case 'r' => PieceType.Rook
              case 'q' => PieceType.Queen
              case 'k' => PieceType.King
              case _   => scala.util.boundary.break(Left(s"Unknown piece character '$char'"))
            }

            val piece = Piece(color, pt)
            mailbox(sq.index) = piece

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
        if (file != 8) scala.util.boundary.break(Left(s"Rank $rankIndex must have 8 files, found $file"))
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
          mailbox = Mailbox.fromBuilder(mailbox),
          flags = flags,
          enPassant = enPassantBb,
          fullMoveNumber = fullMove
        )
      )
    } catch {
      case e: Exception => Left(s"FEN parsing error: ${e.getMessage}")
    }
  }

  /** Serialises a [[GameState]] back to a FEN string.
    *
    * Produces all six standard FEN fields, plus an optional 7th field if the dice pool is non-empty. Piece placement is
    * written rank-8-first (as required by the standard). This method is the inverse of [[parse]]: `parse(serialize(s))`
    * always yields `Right(s)` for a valid state.
    *
    * @param state
    *   the game state to encode
    * @return
    *   a valid FEN string representing `state`
    */
  def serialize(state: GameState): String = {
    val res = new StringBuilder()
    var r   = 7
    while (r >= 0) {
      var empty = 0
      var f     = 0
      while (f < 8) {
        val sq    = Square.fromIndex(r * 8 + f)
        val piece = state.mailbox(sq)
        if (!piece.isEmpty) {
          if (empty > 0) res.append(empty.toString)
          empty = 0
          val char = piece.pieceType.asNotation
          res.append(if (piece.color.isWhite) char.toUpperCase else char.toLowerCase)
        } else {
          empty += 1
        }
        f += 1
      }
      if (empty > 0) res.append(empty.toString)
      if (r > 0) res.append("/")
      r -= 1
    }

    res.append(" ")
    res.append(if (state.flags.activeColor.isWhite) "w" else "b")
    res.append(" ")

    val cr = state.flags.castlingRights
    if (cr == 0) res.append("-")
    else {
      if ((cr & 1) != 0) res.append("K")
      if ((cr & 2) != 0) res.append("Q")
      if ((cr & 4) != 0) res.append("k")
      if ((cr & 8) != 0) res.append("q")
    }

    res.append(" ")
    if (state.enPassant.isEmpty) {
      res.append("-")
    } else {
      var ep = state.enPassant.value
      while (ep != 0) {
        val sqIdx = java.lang.Long.numberOfTrailingZeros(ep)
        res.append(Square.fromIndex(sqIdx).toNotation)
        ep &= ep - 1
      }
    }
    res.append(" ")
    res.append(state.flags.halfMoveClock.toString)
    res.append(" ")
    res.append(state.fullMoveNumber.toString)

    val pool = state.flags.dicePool
    if (pool.nonEmpty) {
      res.append(" ")
      pool.sorted.foreach { d =>
        val char = d match {
          case 1 => 'p'
          case 2 => 'n'
          case 3 => 'b'
          case 4 => 'r'
          case 5 => 'q'
          case 6 => 'k'
          case _ => '?'
        }
        res.append(if (state.flags.activeColor.isWhite) char.toUpper else char)
      }
    }

    res.toString
  }
}
