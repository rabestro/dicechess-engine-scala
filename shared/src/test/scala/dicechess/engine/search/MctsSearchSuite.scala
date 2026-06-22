package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite
import scala.concurrent.duration.*
import scala.util.Random

class MctsSearchSuite extends FunSuite:

  override def munitTimeout: Duration = 90.seconds

  private def parseFen(fen: String): GameState =
    FenParser.parse(fen).fold(e => fail(s"bad FEN '$fen': $e"), identity)

  test("is registered in BotRegistry as difficulty 7") {
    assertEquals(BotRegistry.getAlgorithm("mcts"), Some(MctsSearch))
    val info = BotRegistry.availableBots.find(_.id == "mcts").getOrElse(fail("mcts not listed"))
    assertEquals(info.difficulty, 7)
    assertEquals(info.name, "Monte-Carlo Tree Search")
  }

  test("prefers an immediate king capture (terminal win score)") {
    val state = parseFen("4k3/8/3N4/8/8/8/8/4K3 w - - 0 1").withDicePool(List(2, 1, 1))
    val best  = MctsSearch
      .findBestMove(state, System.nanoTime() + 10.millis.toNanos, new Random(1))
      .getOrElse(fail("expected a move"))
    assertEquals(best.score, SearchScoring.TerminalWinScore)
    assertEquals(best.moves.last.toSquare, Square('e', 8))
  }

  test("returns None when there is no legal move (forced pass)") {
    val state = parseFen("7k/8/8/8/8/8/8/K7 w - - 0 1").withDicePool(List(2, 2, 2))
    assertEquals(MctsSearch.findBestMove(state, System.nanoTime() + 10.millis.toNanos, new Random(1)), None)
  }

  test("returns a legal, non-terminal turn scored as a win probability") {
    val state = parseFen("4k3/8/8/3n4/3N4/8/8/4K3 w - - 0 1").withDicePool(List(2, 1, 1))
    val best  = MctsSearch
      .findBestMove(state, System.nanoTime() + 50.millis.toNanos, new Random(2))
      .getOrElse(fail("expected a move"))
    assert(best.moves.nonEmpty, "expected a non-empty turn path")
    assert(best.score >= 0, s"score should be non-negative, got ${best.score}")
    assert(best.score < SearchScoring.TerminalWinScore, s"score should be non-terminal, got ${best.score}")
  }

  test("the default-budget overload returns None on a forced pass") {
    val state = parseFen("7k/8/8/8/8/8/8/K7 w - - 0 1").withDicePool(List(2, 2, 2))
    assertEquals(MctsSearch.findBestMove(state), None)
  }

  test("a deadline already in the past returns a legal fallback move without running rollouts") {
    val state = parseFen("4k3/8/8/3n4/3N4/8/8/4K3 w - - 0 1").withDicePool(List(2, 1, 1))
    val best  = MctsSearch
      .findBestMove(state, System.nanoTime() - 1_000_000L, new Random(1))
      .getOrElse(fail("expected a move"))
    assert(best.moves.nonEmpty, "expected a non-empty turn path")
    assert(best.score < SearchScoring.TerminalWinScore, s"fallback should be non-terminal, got ${best.score}")
  }

  test("an immediate king capture is taken even when the deadline has passed") {
    val state = parseFen("4k3/8/3N4/8/8/8/8/4K3 w - - 0 1").withDicePool(List(2, 1, 1))
    val best  = MctsSearch
      .findBestMove(state, System.nanoTime() - 1_000_000L, new Random(1))
      .getOrElse(fail("expected a move"))
    assertEquals(best.score, SearchScoring.TerminalWinScore)
    assertEquals(best.moves.last.toSquare, Square('e', 8))
  }

  test("the wall-clock TimeBudgetedSearch overload returns a legal move within the budget") {
    val state = parseFen("4k3/8/8/3n4/3N4/8/8/4K3 w - - 0 1").withDicePool(List(2, 1, 1))
    val best  = MctsSearch
      .findBestMove(state, System.nanoTime() + 30.millis.toNanos, new Random(1))
      .getOrElse(fail("expected a move"))
    assert(best.moves.nonEmpty, "expected a non-empty turn path")
    assert(
      best.score < SearchScoring.TerminalWinScore,
      s"non-capturing position should be non-terminal, got ${best.score}"
    )
  }
