package dicechess.engine.movegen

import munit.FunSuite
import dicechess.engine.domain.*

class MoveGeneratorSpec extends FunSuite:

  private def parse(fen: String): GameState =
    FenParser.parse(fen).getOrElse(sys.error(s"Failed to parse FEN: $fen"))

  // ── generateMoves ─────────────────────────────────────────────────────────

  test("generateMoves returns Nil for invalid dice roll (0)") {
    val state = parse(FenParser.InitialPosition)
    assertEquals(MoveGenerator.generateMoves(state, 0), Nil)
  }

  test("generateMoves returns Nil for invalid dice roll (7)") {
    val state = parse(FenParser.InitialPosition)
    assertEquals(MoveGenerator.generateMoves(state, 7), Nil)
  }

  test("generateMoves returns pawn moves for dice=1") {
    val state = parse(FenParser.InitialPosition)
    val moves = MoveGenerator.generateMoves(state, 1)
    assert(moves.nonEmpty)
    assert(moves.forall(m => state.mailbox.get(m.fromSquare).exists(_.pieceType == PieceType.Pawn)))
  }

  // ── Pawn captures (non-promotion) ─────────────────────────────────────────

  test("pawn captures enemy piece without promoting") {
    // White pawn on e4, black pawn on d5 — normal diagonal capture
    val fen      = "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
    val state    = parse(fen)
    val moves    = MoveGenerator.generateMoves(state, 1) // pawns
    val captures = moves.filter(m => m.fromSquare == Square('e', 4) && m.isCapture && !m.isPromotion)
    assert(captures.exists(_.toSquare == Square('d', 5)), "Expected pawn capture e4xd5")
  }

  test("pawn can capture two enemies diagonally") {
    // White pawn on d4, black pawns on c5 and e5
    val fen       = "rnbqkbnr/pp3ppp/8/2p1p3/3P4/8/PPP2PPP/RNBQKBNR w KQkq - 0 3"
    val state     = parse(fen)
    val moves     = MoveGenerator.generateMoves(state, 1)
    val dCaptures = moves.filter(m => m.fromSquare == Square('d', 4) && m.isCapture && !m.isPromotion)
    assertEquals(dCaptures.size, 2)
  }

  // ── Pawn promotion captures ────────────────────────────────────────────────

  test("pawn promotion capture generates 4 moves per target") {
    val fen          = "5rrk/6P1/8/8/8/8/8/4K3 w - - 0 1"
    val state        = parse(fen)
    val moves        = MoveGenerator.generateMoves(state, 1)
    val promCaptures = moves.filter(m => m.isPromotion && m.isCapture)
    assert(promCaptures.size >= 4, s"Expected at least 4 promotion captures, got ${promCaptures.size}")
  }

  // ── Castling generation ────────────────────────────────────────────────────

  test("castling moves included when path is clear") {
    val fen     = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val state   = parse(fen)
    val moves   = MoveGenerator.generateAllMoves(state)
    val castles = moves.filter(m => m.flags == Move.KingCastle || m.flags == Move.QueenCastle)
    assert(castles.exists(_.flags == Move.KingCastle), "Expected king-side castle")
    assert(castles.exists(_.flags == Move.QueenCastle), "Expected queen-side castle")
  }

  test("castling not generated when path is blocked") {
    // Knight on f1 blocks king-side castling
    val fen   = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3KN1R w KQkq - 0 1"
    val state = parse(fen)
    val moves = MoveGenerator.generateAllMoves(state)
    assert(!moves.exists(_.flags == Move.KingCastle), "King-side castle should be blocked")
  }

  test("castling not generated when king would pass through check") {
    // Black rook on g8 covers g1 — white king-side castling target square is unsafe
    val fen   = "4k1r1/8/8/8/8/8/8/R3K2R w KQ - 0 1"
    val state = parse(fen)
    val moves = MoveGenerator.generateAllMoves(state)
    assert(!moves.exists(_.flags == Move.KingCastle), "King-side castle through check should be blocked")
  }

  test("black queen-side castling included in generated moves") {
    val fen   = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R b KQkq - 0 1"
    val state = parse(fen)
    val moves = MoveGenerator.generateAllMoves(state)
    assert(moves.exists(_.flags == Move.QueenCastle), "Expected black queen-side castle")
  }

  // ── generatePawnMoves: early return ───────────────────────────────────────

  test("generateMoves returns Nil when no pawns of active color") {
    // Position with no white pawns
    val fen   = "rnbqkbnr/pppppppp/8/8/8/8/8/RNBQKBNR w KQkq - 0 1"
    val state = parse(fen)
    val moves = MoveGenerator.generateMoves(state, 1) // pawns dice
    assertEquals(moves, Nil)
  }
