package dicechess.engine.bench

import dicechess.engine.search.*
import munit.FunSuite
import scala.util.Random

class BotMatchRunnerSpec extends FunSuite:

  test("simulateGame completes successfully and returns a valid outcome") {
    val rand    = new Random(42)
    val outcome = BotMatchRunner.simulateGame(GreedySearch, GreedySearch, rand)

    // Outcome must be either a Win or a Draw
    assert(outcome == GameOutcome.Draw || outcome.isInstanceOf[GameOutcome.Win])
  }

  test("runMatch executes the correct number of games and enforces alternating colors") {
    val gamesPerColor = 5
    val matchResult   = BotMatchRunner.runMatch(GreedySearch, GreedySearch, gamesPerColor)

    assertEquals(matchResult.totalGames, gamesPerColor * 2)
    assertEquals(matchResult.winsAsWhite + matchResult.lossesAsWhite + matchResult.drawsAsWhite, gamesPerColor)
    assertEquals(matchResult.winsAsBlack + matchResult.lossesAsBlack + matchResult.drawsAsBlack, gamesPerColor)
    assert(matchResult.durationMs >= 0)
  }

  test("Random vs Greedy results in higher win rate for Greedy") {
    val gamesPerColor = 5
    // Random is expected to perform extremely poorly against Greedy
    val matchResult = BotMatchRunner.runMatch(RandomSearch, GreedySearch, gamesPerColor)

    val totalWinsForRandom = matchResult.winsAsWhite + matchResult.winsAsBlack
    val totalWinsForGreedy = matchResult.lossesAsWhite + matchResult.lossesAsBlack

    assert(
      totalWinsForGreedy >= totalWinsForRandom,
      s"Expected Greedy to beat Random, but got Greedy: $totalWinsForGreedy, Random: $totalWinsForRandom"
    )
  }
