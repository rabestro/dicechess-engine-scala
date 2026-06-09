package dicechess.engine.domain

import scala.util.boundary, boundary.break

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
  def parse(fen: String): Either[String, GameState] = try
    boundary {
      val parts = fen.split(" ")
      if parts.length < 4 then break(Left("Invalid FEN: insufficient parts"))

      val board       = parts(0)
      val activeColor = parts(1) match
        case "w"   => Color.White
        case "b"   => Color.Black
        case other => break(Left(s"Invalid active color '$other'"))
      val castling    = parts(2)
      val castlingInt = parseCastling(castling)

      val (epFiles, enPassantBb) = parseEnPassant(parts(3))
      val halfMove               = if parts.length > 4 then parseHalfMove(parts(4)) else 0
      val fullMove               = if parts.length > 5 then parseFullMove(parts(5)) else 1
      val dicePool               = if parts.length >= 7 then parseDicePool(parts(6)) else Nil

      val flags = GameFlags.fromList(activeColor, castlingInt, epFiles, dicePool, halfMove)

      val ranks = board.split("/")
      if ranks.length != 8 then break(Left(s"Invalid FEN: board must have 8 ranks, found ${ranks.length}"))

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
      while r < ranks.length do {
        val rankStr   = ranks(r)
        val rankIndex = 7 - r
        var file      = 0
        var i         = 0
        while i < rankStr.length do {
          val char = rankStr.charAt(i)
          if char.isDigit then {
            file += char.asDigit
          } else {
            if file >= 8 then break(Left(s"Rank $rankIndex overflows 8 files"))
            val sq    = Square.fromIndex(rankIndex * 8 + file)
            val color = if char.isUpper then Color.White else Color.Black
            val pt    = char.toLower match {
              case 'p' => PieceType.Pawn
              case 'n' => PieceType.Knight
              case 'b' => PieceType.Bishop
              case 'r' => PieceType.Rook
              case 'q' => PieceType.Queen
              case 'k' => PieceType.King
              case _   => break(Left(s"Unknown piece character '$char'"))
            }

            val piece = Piece(color, pt)
            mailbox(sq.index) = piece

            val bb = Bitboard.fromSquare(sq)
            if color.isWhite then whitePieces |= bb else blackPieces |= bb

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
        if file != 8 then break(Left(s"Rank $rankIndex must have 8 files, found $file"))
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
    }
  catch {
    case e: Exception => Left(s"FEN parsing error: ${e.getMessage}")
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
    while r >= 0 do {
      var empty = 0
      var f     = 0
      while f < 8 do {
        val sq    = Square.fromIndex(r * 8 + f)
        val piece = state.mailbox(sq)
        if !piece.isEmpty then {
          if empty > 0 then res.append(empty.toString)
          empty = 0
          val char = piece.pieceType.asNotation
          res.append(if piece.color.isWhite then char.toUpperCase else char.toLowerCase)
        } else {
          empty += 1
        }
        f += 1
      }
      if empty > 0 then res.append(empty.toString)
      if r > 0 then res.append("/")
      r -= 1
    }

    res.append(" ")
    res.append(if state.flags.activeColor.isWhite then "w" else "b")
    res.append(" ")

    val cr = state.flags.castlingRights
    if cr == 0 then res.append("-")
    else {
      if (cr & 1) != 0 then res.append("K")
      if (cr & 2) != 0 then res.append("Q")
      if (cr & 4) != 0 then res.append("k")
      if (cr & 8) != 0 then res.append("q")
    }

    res.append(" ")
    if state.enPassant.isEmpty then {
      res.append("-")
    } else {
      var ep = state.enPassant.value
      while ep != 0 do {
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
    if pool.nonEmpty then {
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
        res.append(if state.flags.activeColor.isWhite then char.toUpper else char)
      }
    }

    res.toString
  }

  /** Parses the castling FEN string to an integer bitmask. Uses a single pass `foreach` which is highly optimized in
    * Scala 3. Directly breaks the enclosing boundary on invalid characters.
    */
  private inline def parseCastling(castling: String)(using boundary.Label[Either[String, GameState]]): Int = {
    val len = castling.length
    if len < 1 || len > 4 then break(Left(s"Invalid castling field length: $len"))

    if castling == "-" then 0
    else {
      var castlingInt = 0
      castling.foreach { c =>
        val bit = c match {
          case 'K' => 1
          case 'Q' => 2
          case 'k' => 4
          case 'q' => 8
          case _   => break(Left(s"Invalid castling character '$c'"))
        }
        if (castlingInt & bit) != 0 then break(Left(s"Duplicate castling character '$c'"))
        castlingInt |= bit
      }
      castlingInt
    }
  }

  /** Parses the en-passant FEN string. Returns a tuple `(epFiles, enPassantBb)`. Uses an optimized direct character
    * scan instead of creating intermediate strings and objects.
    */
  private inline def parseEnPassant(
      enPassantField: String
  )(using boundary.Label[Either[String, GameState]]): (Int, Bitboard) =
    if enPassantField == "-" then (0, Bitboard.empty)
    else {
      val len = enPassantField.length
      if len == 0 || len % 2 != 0 then break(Left(s"Invalid en-passant field '$enPassantField'"))

      var epFiles     = 0
      var enPassantBb = Bitboard.empty
      var idx         = 0
      while idx < len do {
        val fileChar = enPassantField.charAt(idx)
        val rankChar = enPassantField.charAt(idx + 1)

        if fileChar < 'a' || fileChar > 'h' || rankChar < '1' || rankChar > '8' then
          break(Left(s"Invalid en-passant notation '$fileChar$rankChar'"))

        val sq = Square.fromIndex((rankChar - '1') * 8 + (fileChar - 'a'))
        if enPassantBb.contains(sq) then break(Left(s"Duplicate en-passant square '$fileChar$rankChar'"))

        epFiles |= (1 << (fileChar - 'a'))
        enPassantBb = enPassantBb.add(sq)
        idx += 2
      }
      (epFiles, enPassantBb)
    }

  /** Parses the dice pool FEN string into a sorted list of dice values. */
  private inline def parseDicePool(
      poolField: String
  )(using boundary.Label[Either[String, GameState]]): List[Int] =
    if poolField == "-" then Nil
    else {
      var list: List[Int] = Nil
      var idx             = 0
      val len             = poolField.length

      while idx < len do {
        val c     = poolField.charAt(idx)
        val digit = c match {
          case 'p' | 'P' => 1
          case 'n' | 'N' => 2
          case 'b' | 'B' => 3
          case 'r' | 'R' => 4
          case 'q' | 'Q' => 5
          case 'k' | 'K' => 6
          case _         => break(Left(s"Invalid dice-pool character '$c'"))
        }
        list = digit :: list
        idx += 1
      }
      list.sorted
    }

  private inline def parseHalfMove(
      s: String
  )(using boundary.Label[Either[String, GameState]]): Int = {
    val v = parsePositiveInt(s)
    if v >= 0 then v else break(Left(s"Invalid half-move clock '$s'"))
  }

  private inline def parseFullMove(
      s: String
  )(using boundary.Label[Either[String, GameState]]): Int = {
    val v = parsePositiveInt(s)
    if v >= 1 then v else break(Left(s"Invalid full-move number '$s'"))
  }

  /** Parses a positive integer from a string without allocating Option or throwing exceptions. */
  private inline def parsePositiveInt(s: String): Int = {
    var res   = 0
    var i     = 0
    val len   = s.length
    var valid = len > 0
    while i < len && valid do {
      val c = s.charAt(i)
      if c >= '0' && c <= '9' then res = res * 10 + (c - '0')
      else valid = false
      i += 1
    }
    if valid then res else -1
  }
}
