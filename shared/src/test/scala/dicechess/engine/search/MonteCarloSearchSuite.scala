package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite
import scala.util.Random

class MonteCarloSearchSuite extends FunSuite:

  private def parseFen(fen: String): GameState =
    FenParser.parse(fen).fold(e => fail(s"bad FEN '$fen': $e"), identity)

  // Tiny budget so the rollout-driven tests stay fast under coverage and on Scala.js.
  private val tinyConfig = MonteCarloConfig(rollouts = 8, maxPlies = 6)

  private def buildState(placement: List[(Square, Piece)], activeColor: Color): GameState =
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

  /** White to move with a piece of every type attacking the lone Black king on e4: every dice roll captures, so the
    * Monte-Carlo win probability is ~1 and each rollout resolves on the first ply (cheap).
    */
  private val whiteAlwaysWins: GameState =
    buildState(
      List(
        Square('e', 4) -> Piece(Color.Black, PieceType.King),
        Square('d', 3) -> Piece(Color.White, PieceType.Pawn),
        Square('c', 3) -> Piece(Color.White, PieceType.Knight),
        Square('h', 7) -> Piece(Color.White, PieceType.Bishop),
        Square('h', 4) -> Piece(Color.White, PieceType.Rook),
        Square('a', 8) -> Piece(Color.White, PieceType.Queen),
        Square('d', 4) -> Piece(Color.White, PieceType.King)
      ),
      Color.White
    )

  test("is registered in BotRegistry as difficulty 7") {
    assertEquals(BotRegistry.getAlgorithm("monte-carlo"), Some(MonteCarloSearch))
    val info = BotRegistry.availableBots.find(_.id == "monte-carlo").getOrElse(fail("monte-carlo not listed"))
    assertEquals(info.difficulty, 7)
    assertEquals(info.name, "Monte-Carlo")
  }

  test("prefers an immediate king capture (terminal win score)") {
    // White queen on e4 can capture the Black king on e8 down the open e-file; the dice include a queen die.
    val state = parseFen("4k3/8/8/8/4Q3/8/8/K7 w - - 0 1").withDicePool(List(5, 1, 1))
    val best  = MonteCarloSearch.findBestMove(state, tinyConfig, new Random(1)).getOrElse(fail("expected a move"))
    assertEquals(best.score, SearchScoring.TerminalWinScore)
    // The winning turn ends on the king's square.
    assertEquals(best.moves.last.toSquare, Square('e', 8))
  }

  test("returns None when there is no legal move (forced pass)") {
    // Lone White king, dice are all knight dice — no knight exists, so no move is possible.
    val state = parseFen("7k/8/8/8/8/8/8/K7 w - - 0 1").withDicePool(List(2, 2, 2))
    assertEquals(MonteCarloSearch.findBestMove(state, tinyConfig, new Random(1)), None)
  }

  test("returns a legal, non-terminal turn scored as a win probability") {
    // Queens facing off; a queen die yields several candidate turns, none of which captures a king.
    val state = parseFen("4k3/8/8/3q4/3Q4/8/8/4K3 w - - 0 1").withDicePool(List(5, 4, 1))
    val best  = MonteCarloSearch.findBestMove(state, tinyConfig, new Random(2)).getOrElse(fail("expected a move"))
    assert(best.moves.nonEmpty, "expected a non-empty turn path")
    assert(best.score >= 0, s"score should be non-negative, got ${best.score}")
    assert(best.score < SearchScoring.TerminalWinScore, s"score should be non-terminal, got ${best.score}")
  }

  test("is deterministic for a fixed seed and budget") {
    val state = parseFen("4k3/8/8/3q4/3Q4/8/8/4K3 w - - 0 1").withDicePool(List(5, 4, 1))
    val a     = MonteCarloSearch.findBestMove(state, tinyConfig, new Random(7))
    val b     = MonteCarloSearch.findBestMove(state, tinyConfig, new Random(7))
    assertEquals(a.map(_.moves), b.map(_.moves))
  }

  test("the default-budget overload returns None on a forced pass") {
    val state = parseFen("7k/8/8/8/8/8/8/K7 w - - 0 1").withDicePool(List(2, 2, 2))
    assertEquals(MonteCarloSearch.findBestMove(state), None)
  }

  test("offers and accepts a double in a clearly winning position") {
    assert(MonteCarloSearch.shouldOfferDouble(whiteAlwaysWins, currentStake = 1), "should offer a double when winning")
    assert(
      MonteCarloSearch.shouldAcceptDouble(whiteAlwaysWins, currentStake = 2),
      "should accept a double when winning"
    )
  }
