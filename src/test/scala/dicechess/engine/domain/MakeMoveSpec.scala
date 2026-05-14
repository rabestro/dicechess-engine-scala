package dicechess.engine.domain

import munit.FunSuite

class MakeMoveSpec extends FunSuite:

  private def parse(fen: String): GameState =
    FenParser.parse(fen).getOrElse(sys.error(s"Failed to parse FEN: $fen"))

  // ── Quiet move ────────────────────────────────────────────────────────────

  test("quiet move updates bitboards and mailbox") {
    val state  = parse(FenParser.InitialPosition)
    val mv     = Move(Square('e', 2), Square('e', 3), Move.QuietMove)
    val result = state.makeMove(mv)

    assertEquals(result.mailbox.get(Square('e', 2)), None)
    assertEquals(result.mailbox.get(Square('e', 3)), Some(Piece(Color.White, PieceType.Pawn)))
    assertEquals(result.activeColor, Color.Black)
    assertEquals(result.enPassant, None)
  }

  // ── Double pawn push & en passant square ─────────────────────────────────

  test("double pawn push sets en passant square") {
    val state  = parse(FenParser.InitialPosition)
    val mv     = Move(Square('e', 2), Square('e', 4), Move.DoublePawnPush)
    val result = state.makeMove(mv)

    assertEquals(result.enPassant, Some(Square('e', 3)))
    assertEquals(result.mailbox.get(Square('e', 4)), Some(Piece(Color.White, PieceType.Pawn)))
  }

  test("black double pawn push sets en passant square on rank 6") {
    val fen    = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
    val state  = parse(fen)
    val mv     = Move(Square('e', 7), Square('e', 5), Move.DoublePawnPush)
    val result = state.makeMove(mv)

    assertEquals(result.enPassant, Some(Square('e', 6)))
  }

  // ── En passant capture ────────────────────────────────────────────────────

  test("en passant capture removes victim pawn") {
    // White pawn on e5, black pawn just moved d7-d5
    val fen   = "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3"
    val state = parse(fen)

    val mv     = Move(Square('e', 5), Square('d', 6), Move.EnPassantCapture)
    val result = state.makeMove(mv)

    assertEquals(result.mailbox.get(Square('d', 5)), None) // victim removed
    assertEquals(result.mailbox.get(Square('e', 5)), None) // attacker gone
    assertEquals(result.mailbox.get(Square('d', 6)), Some(Piece(Color.White, PieceType.Pawn)))
    assertEquals(result.enPassant, None)
  }

  // ── Capture ───────────────────────────────────────────────────────────────

  test("capture removes enemy piece from bitboards") {
    val fen   = "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
    val state = parse(fen)

    val mv     = Move(Square('e', 4), Square('d', 5), Move.Capture)
    val result = state.makeMove(mv)

    assertEquals(result.mailbox.get(Square('d', 5)), Some(Piece(Color.White, PieceType.Pawn)))
    assertEquals(result.blackPieces.contains(Square('d', 5)), false)
  }

  // ── Castling ──────────────────────────────────────────────────────────────

  test("white king-side castling moves king and rook") {
    // King and rook in place, path clear
    val fen   = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val state = parse(fen)

    val mv     = Move(Square('e', 1), Square('g', 1), Move.KingCastle)
    val result = state.makeMove(mv)

    assertEquals(result.mailbox.get(Square('g', 1)), Some(Piece(Color.White, PieceType.King)))
    assertEquals(result.mailbox.get(Square('f', 1)), Some(Piece(Color.White, PieceType.Rook)))
    assertEquals(result.mailbox.get(Square('e', 1)), None)
    assertEquals(result.mailbox.get(Square('h', 1)), None)
  }

  test("white queen-side castling moves king and rook") {
    val fen   = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val state = parse(fen)

    val mv     = Move(Square('e', 1), Square('c', 1), Move.QueenCastle)
    val result = state.makeMove(mv)

    assertEquals(result.mailbox.get(Square('c', 1)), Some(Piece(Color.White, PieceType.King)))
    assertEquals(result.mailbox.get(Square('d', 1)), Some(Piece(Color.White, PieceType.Rook)))
    assertEquals(result.mailbox.get(Square('e', 1)), None)
    assertEquals(result.mailbox.get(Square('a', 1)), None)
  }

  test("black king-side castling moves king and rook") {
    val fen   = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R b KQkq - 0 1"
    val state = parse(fen)

    val mv     = Move(Square('e', 8), Square('g', 8), Move.KingCastle)
    val result = state.makeMove(mv)

    assertEquals(result.mailbox.get(Square('g', 8)), Some(Piece(Color.Black, PieceType.King)))
    assertEquals(result.mailbox.get(Square('f', 8)), Some(Piece(Color.Black, PieceType.Rook)))
    assertEquals(result.mailbox.get(Square('h', 8)), None)
  }

  // ── Castling rights ───────────────────────────────────────────────────────

  test("king move removes all castling rights for that color") {
    val state  = parse(FenParser.InitialPosition)
    val mv     = Move(Square('e', 1), Square('e', 2), Move.QuietMove)
    val result = state.makeMove(mv)

    assert(!result.castlingRights.contains('K'))
    assert(!result.castlingRights.contains('Q'))
    assert(result.castlingRights.contains('k'))
  }

  test("rook move removes corresponding castling right") {
    val fen    = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val state  = parse(fen)
    val mv     = Move(Square('h', 1), Square('h', 2), Move.QuietMove)
    val result = state.makeMove(mv)

    assert(!result.castlingRights.contains('K'))
    assert(result.castlingRights.contains('Q'))
  }

  test("capturing enemy rook removes its castling right") {
    val fen   = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/1B2K2R w Kkq - 0 1"
    val state = parse(fen)
    // Bishop on b1 captures rook on a8 — contrived but tests the rule
    val mv     = Move(Square('b', 1), Square('a', 8), Move.Capture)
    val result = state.makeMove(mv)

    assert(!result.castlingRights.contains('q'))
    assert(result.castlingRights.contains('k'))
  }

  // ── Half-move clock ───────────────────────────────────────────────────────

  test("pawn move resets half-move clock") {
    val fen    = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 10 6"
    val state  = parse(fen)
    val mv     = Move(Square('e', 2), Square('e', 3), Move.QuietMove)
    val result = state.makeMove(mv)

    assertEquals(result.halfMoveClock, 0)
  }

  test("non-pawn quiet move increments half-move clock") {
    val fen    = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 4 3"
    val state  = parse(fen)
    val mv     = Move(Square('g', 1), Square('f', 3), Move.QuietMove)
    val result = state.makeMove(mv)

    assertEquals(result.halfMoveClock, 5)
  }

  // ── Full-move number ──────────────────────────────────────────────────────

  test("full-move number increments after black moves") {
    val fen    = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
    val state  = parse(fen)
    val mv     = Move(Square('e', 7), Square('e', 6), Move.QuietMove)
    val result = state.makeMove(mv)

    assertEquals(result.fullMoveNumber, 2)
  }

  test("full-move number does not increment after white moves") {
    val state  = parse(FenParser.InitialPosition)
    val mv     = Move(Square('e', 2), Square('e', 3), Move.QuietMove)
    val result = state.makeMove(mv)

    assertEquals(result.fullMoveNumber, 1)
  }
