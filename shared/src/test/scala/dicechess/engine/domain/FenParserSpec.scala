package dicechess.engine.domain

import munit.FunSuite

class FenParserSpec extends FunSuite:

  test("FenParser should correctly parse the standard initial position") {
    val fen    = FenParser.InitialPosition
    val parsed = FenParser.parse(fen)

    assert(parsed.isRight)
    val state = parsed.getOrElse(sys.error("Parsing failed"))

    assertEquals(state.activeColor, Color.White)
    assertEquals(state.castlingRights, "KQkq")
    assertEquals(state.enPassant, Bitboard.empty)
    assertEquals(state.halfMoveClock, 0)
    assertEquals(state.fullMoveNumber, 1)

    // Bitboard property checks
    assertEquals(state.whitePieces.count, 16)
    assertEquals(state.blackPieces.count, 16)
    assertEquals(state.pawns.count, 16) // 8 white + 8 black
    assertEquals(state.kings.count, 2)
    assertEquals(state.mailbox.toArray.count(!_.isEmpty), 32)
  }

  test("FenParser should serialize the initial position back to identical FEN") {
    val fen        = FenParser.InitialPosition
    val parsed     = FenParser.parse(fen).toOption.get
    val serialized = FenParser.serialize(parsed)

    assertEquals(serialized, fen)
  }

  test("FenParser should correctly parse various castling rights") {
    val base = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w "
    val tail = " - 0 1"

    val rights = List("KQkq", "KQ", "kq", "Kk", "Q", "-", "q")
    for r <- rights do
      val fen    = base + r + tail
      val parsed = FenParser.parse(fen).toOption.get

      assertEquals(FenParser.serialize(parsed), fen)

      val expectedInt = r.foldLeft(0) { (acc, c) =>
        c match
          case 'K' => acc | 1
          case 'Q' => acc | 2
          case 'k' => acc | 4
          case 'q' => acc | 8
          case _   => acc
      }
      assertEquals(parsed.flags.castlingRights, expectedInt)
  }

  test("FenParser should return Left for invalid castling characters") {
    val invalidFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQx - 0 1"
    val parsed     = FenParser.parse(invalidFen)

    assert(parsed.isLeft)
    assert(parsed.left.toOption.get.contains("Invalid castling character 'x'"))

    val hyphenInsideFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w K-q - 0 1"
    val parsedHyphen    = FenParser.parse(hyphenInsideFen)
    assert(parsedHyphen.isLeft)
    assert(parsedHyphen.left.toOption.get.contains("Invalid castling character '-'"))
  }

  test("FenParser should return Left for invalid castling field length") {
    val emptyCastlingFen =
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w  - 0 1" // Notice the double space making an empty field
    val emptyParsed = FenParser.parse(emptyCastlingFen)
    assert(emptyParsed.isLeft)
    assert(emptyParsed.left.toOption.get.contains("Invalid castling field length"))

    val tooLongFen    = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkqK - 0 1"
    val tooLongParsed = FenParser.parse(tooLongFen)
    assert(tooLongParsed.isLeft)
    assert(tooLongParsed.left.toOption.get.contains("Invalid castling field length: 5"))
  }

  test("FenParser should correctly handle complex mid-game positions") {
    val complexFen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
    val parsed     = FenParser.parse(complexFen)

    assert(parsed.isRight)
    val state      = parsed.toOption.get
    val serialized = FenParser.serialize(state)

    assertEquals(serialized, complexFen)
  }

  test("FenParser should correctly parse en passant targets") {
    val fen    = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    val parsed = FenParser.parse(fen).toOption.get

    assertEquals(parsed.enPassant, Bitboard.fromSquare(Square('e', 3)))
    assertEquals(FenParser.serialize(parsed), fen)
  }

  test("FenParser should correctly parse and serialize multiple en passant targets") {
    val fen    = "rnbqkbnr/pppppppp/8/8/P1P1P3/8/1P1P1PPP/RNBQKBNR b KQkq a3c3e3 0 1"
    val parsed = FenParser.parse(fen)

    assert(parsed.isRight)
    val state = parsed.toOption.get
    assertEquals(
      state.enPassant,
      Bitboard.fromSquare(Square('a', 3)) | Bitboard.fromSquare(Square('c', 3)) | Bitboard.fromSquare(Square('e', 3))
    )
    assertEquals(FenParser.serialize(state), fen)
  }

  test("FenParser should return Left for an invalid board layout") {
    // 9 files on the first rank
    val invalidFen = "rnbqkbnrP/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val parsed     = FenParser.parse(invalidFen)

    assert(parsed.isLeft)
    assert(parsed.left.toOption.get.contains("overflows 8 files"))
  }

  test("FenParser should return Left for unknown piece characters") {
    val invalidFen = "rnbqkbnr/ppppXppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val parsed     = FenParser.parse(invalidFen)

    assert(parsed.isLeft)
    assert(parsed.left.toOption.get.contains("Unknown piece character 'X'"))
  }

  test("FenParser should correctly parse and serialize DFEN with 7th field dice pool") {
    val fen    = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 PPP"
    val parsed = FenParser.parse(fen)

    assert(parsed.isRight)
    val state = parsed.toOption.get
    assertEquals(state.dicePool, List(1, 1, 1))
    assertEquals(FenParser.serialize(state), fen)
  }

  test("FenParser should support backward compatibility for 6-field standard FEN and default dicePool to Nil") {
    val fen    = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val parsed = FenParser.parse(fen)

    assert(parsed.isRight)
    val state = parsed.toOption.get
    assertEquals(state.dicePool, Nil)
    assertEquals(FenParser.serialize(state), fen)
  }

  test("FenParser should parse 7-field FEN with empty dice pool '-' and serialize to 6-field FEN") {
    val fen    = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 -"
    val parsed = FenParser.parse(fen)

    assert(parsed.isRight)
    val state = parsed.toOption.get
    assertEquals(state.dicePool, Nil)
    assertEquals(FenParser.serialize(state), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
  }
