package dicechess.engine.domain

import munit.FunSuite

class FenParserSpec extends FunSuite:

  test("FenParser should correctly parse the standard initial position") {
    val fen    = FenParser.InitialPosition
    val parsed = FenParser.parse(fen)

    assert(parsed.isRight)
    val state = parsed.getOrElse(throw new Exception("Parsing failed"))

    assertEquals(state.activeColor, Color.White)
    assertEquals(state.castlingRights, "KQkq")
    assertEquals(state.enPassant, None)
    assertEquals(state.halfMoveClock, 0)
    assertEquals(state.fullMoveNumber, 1)

    // Bitboard property checks
    assertEquals(state.whitePieces.count, 16)
    assertEquals(state.blackPieces.count, 16)
    assertEquals(state.pawns.count, 16) // 8 white + 8 black
    assertEquals(state.kings.count, 2)
    assertEquals(state.mailbox.size, 32)
  }

  test("FenParser should serialize the initial position back to identical FEN") {
    val fen        = FenParser.InitialPosition
    val parsed     = FenParser.parse(fen).toOption.get
    val serialized = FenParser.serialize(parsed)

    assertEquals(serialized, fen)
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

    assertEquals(parsed.enPassant, Some(Square('e', 3)))
    assertEquals(FenParser.serialize(parsed), fen)
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
