package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite
import scala.util.Random

class MonteCarloEquitySpec extends FunSuite:

  // --- Helpers ---

  private def buildState(
      placement: List[(Square, Piece)],
      activeColor: Color
  ): GameState =
    val mb      = Array.fill[Piece](64)(Piece.Empty)
    var white   = Bitboard.empty
    var black   = Bitboard.empty
    var pawns   = Bitboard.empty
    var knights = Bitboard.empty
    var bishops = Bitboard.empty
    var rooks   = Bitboard.empty
    var queens  = Bitboard.empty
    var kings   = Bitboard.empty
    placement.foreach { (sq, p) =>
      mb(sq.index) = p
      val bb = Bitboard.fromSquare(sq)
      if p.color.isWhite then white = white | bb else black = black | bb
      p.pieceType match
        case PieceType.Pawn   => pawns = pawns | bb
        case PieceType.Knight => knights = knights | bb
        case PieceType.Bishop => bishops = bishops | bb
        case PieceType.Rook   => rooks = rooks | bb
        case PieceType.Queen  => queens = queens | bb
        case PieceType.King   => kings = kings | bb
        case _                => ()
    }
    GameState(
      white,
      black,
      pawns,
      knights,
      bishops,
      rooks,
      queens,
      kings,
      mailbox = Mailbox.fromBuilder(mb),
      flags = GameFlags.fromList(activeColor, 0, 0, Nil, 0),
      enPassant = Bitboard.empty,
      fullMoveNumber = 1
    )

  private def parseFen(fen: String): GameState =
    FenParser.parse(fen).fold(e => fail(s"bad FEN '$fen': $e"), identity)

  /** A position where, for the side to move, **every** of the 216 dice rolls allows an immediate king capture: a White
    * piece of each of the six types attacks the lone Black king on e4, so any die value yields a capturing micro-move.
    * Pre-roll White-win probability is therefore exactly `1.0` and each rollout resolves on the first ply.
    */
  private val whiteAlwaysWins: GameState =
    buildState(
      List(
        Square('e', 4) -> Piece(Color.Black, PieceType.King),
        Square('d', 3) -> Piece(Color.White, PieceType.Pawn),   // d3 captures e4
        Square('c', 3) -> Piece(Color.White, PieceType.Knight), // c3 attacks e4
        Square('h', 7) -> Piece(Color.White, PieceType.Bishop), // h7-g6-f5-e4
        Square('h', 4) -> Piece(Color.White, PieceType.Rook),   // h4-g4-f4-e4
        Square('a', 8) -> Piece(Color.White, PieceType.Queen),  // a8-b7-c6-d5-e4
        Square('d', 4) -> Piece(Color.White, PieceType.King)    // d4 adjacent to e4
      ),
      Color.White
    )

  /** A sharp, multi-ply position (queens facing off) used for variance and determinism checks. */
  private val queens = "4k3/8/8/3q4/3Q4/8/8/4K3 w - - 0 1"

  private val midgameFens = List(
    FenParser.InitialPosition,
    queens,
    "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1"
  )

  // --- Convergence ---

  test("converges to the exact value on a 1-ply-decidable position (White wins with probability 1)") {
    val est = MonteCarloEquity.estimate(whiteAlwaysWins, MonteCarloConfig(rollouts = 64), new Random(1))
    assertEqualsDouble(est.whiteWin, 1.0, 1e-9)
    assertEqualsDouble(est.blackWin, 0.0, 1e-9)
    assertEqualsDouble(est.undecided, 0.0, 1e-9)
  }

  test("symmetric Black-to-move position (via colorFlip) yields Black-win probability 1") {
    val blackAlwaysWins = Symmetry.colorFlip(whiteAlwaysWins)
    assert(blackAlwaysWins.activeColor.isBlack)
    val est = MonteCarloEquity.estimate(blackAlwaysWins, MonteCarloConfig(rollouts = 64), new Random(2))
    assertEqualsDouble(est.blackWin, 1.0, 1e-9)
    assertEqualsDouble(est.whiteWin, 0.0, 1e-9)
  }

  // --- Invariants ---

  test("the three outcome masses always sum to 1") {
    midgameFens.zipWithIndex.foreach { (fen, i) =>
      val est = MonteCarloEquity.estimate(parseFen(fen), MonteCarloConfig(rollouts = 10, maxPlies = 6), new Random(i))
      assertEqualsDouble(est.whiteWin + est.blackWin + est.undecided, 1.0, 1e-9)
    }
  }

  test("all probabilities are in [0, 1] and the standard error is non-negative") {
    val est = MonteCarloEquity.estimate(parseFen(queens), MonteCarloConfig(rollouts = 50, maxPlies = 6), new Random(4))
    List(est.whiteWin, est.blackWin, est.undecided).foreach { p =>
      assert(p >= 0.0 && p <= 1.0, s"probability out of range: $p")
    }
    assert(est.standardError >= 0.0)
    assertEquals(est.rollouts, 50)
  }

  test("a horizon of zero plies leaves all mass undecided") {
    val est = MonteCarloEquity.estimate(whiteAlwaysWins, MonteCarloConfig(rollouts = 4, maxPlies = 0), new Random(5))
    assertEqualsDouble(est.undecided, 1.0, 1e-9)
    assertEqualsDouble(est.whiteWin, 0.0, 1e-9)
  }

  // --- Variance reduction (port of the reference self-check) ---

  test("Rao-Blackwell integration beats vanilla Monte-Carlo (variance reduction > 1)") {
    val est =
      MonteCarloEquity.estimate(parseFen(queens), MonteCarloConfig(rollouts = 150, maxPlies = 10), new Random(6))
    assert(!est.varianceReductionVsVanilla.isNaN, "variance-reduction ratio is NaN")
    assert(
      est.varianceReductionVsVanilla > 1.0,
      s"expected variance reduction > 1, got ${est.varianceReductionVsVanilla}"
    )
  }

  // --- Determinism & budgets ---

  test("the same seed yields an identical estimate") {
    val cfg = MonteCarloConfig(rollouts = 50, maxPlies = 6)
    val a   = MonteCarloEquity.estimate(parseFen(queens), cfg, new Random(42))
    val b   = MonteCarloEquity.estimate(parseFen(queens), cfg, new Random(42))
    assertEquals(a, b)
  }

  test("adaptive stopping halts at minRollouts once the target error is met") {
    // A huge target error is satisfied immediately, so it stops at exactly minRollouts.
    val est = MonteCarloEquity.estimate(
      parseFen(queens),
      MonteCarloConfig(rollouts = 5000, maxPlies = 6, targetError = 1.0, minRollouts = 64),
      new Random(7)
    )
    assertEquals(est.rollouts, 64)
  }

  // --- Convenience overloads ---

  test("the fixed-budget overload runs the requested number of rollouts") {
    val est = MonteCarloEquity.estimate(whiteAlwaysWins, 16, new Random(8))
    assertEquals(est.rollouts, 16)
    assertEqualsDouble(est.whiteWin, 1.0, 1e-9)
  }

  test("the default overload resolves a decided position regardless of the random source") {
    // whiteAlwaysWins resolves on ply 1, so the unseeded default is still deterministic here.
    val est = MonteCarloEquity.estimate(whiteAlwaysWins)
    assertEqualsDouble(est.whiteWin, 1.0, 1e-9)
  }
