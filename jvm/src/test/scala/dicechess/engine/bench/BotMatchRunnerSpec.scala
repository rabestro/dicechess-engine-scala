package dicechess.engine.bench

import dicechess.engine.domain.Color
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

  // ---- Time-controlled arena: pure helpers (deterministic, no wall-clock) ----

  test("tickClock: credits the increment on a non-flag and flags when the clock goes negative") {
    assertEquals(BotMatchRunner.tickClock(1000, 300, 100), (800L, false))
    assertEquals(BotMatchRunner.tickClock(100, 250, 50), (-150L, true))
    // Exact spend is not a flag; the increment is still credited.
    assertEquals(BotMatchRunner.tickClock(500, 500, 100), (100L, false))
  }

  test("LatencyStats.from: nearest-rank percentiles over (unsorted) samples") {
    assertEquals(LatencyStats.from(Nil), LatencyStats.empty)
    assertEquals(LatencyStats.from(List(42)), LatencyStats(1, 42, 42, 42, 42))
    val ten = LatencyStats.from(List(10, 20, 30, 40, 50, 60, 70, 80, 90, 100))
    assertEquals(ten, LatencyStats(10, 50, 100, 100, 100))
    // Sorting is applied: median of {10,20,30} is 20.
    assertEquals(LatencyStats.from(List(30, 10, 20)).p50Ms, 20L)
  }

  test("TimeControl.label and TimedMatchResult.scorePercent") {
    assertEquals(TimeControl.ofSeconds(60, 0).label, "60s")
    assertEquals(TimeControl.ofSeconds(180, 2).label, "180s+2s")
    assertEquals(TimeControl.ofSeconds(60, 0), TimeControl(60000L, 0L))
    val r =
      TimedMatchResult(TimeControl.ofSeconds(60, 0), 10, wins = 4, losses = 4, draws = 2, 0, 0, LatencyStats.empty, 0)
    assertEqualsDouble(r.scorePercent, 50.0, 1e-9)
  }

  test("parsePresets: parses 'base' and 'base+inc' second specs") {
    assertEquals(
      TimedArenaRunner.parsePresets("1+0,3+2,10+10"),
      List(TimeControl(1000L, 0L), TimeControl(3000L, 2000L), TimeControl(10000L, 10000L))
    )
    assertEquals(TimedArenaRunner.parsePresets("60"), List(TimeControl(60000L, 0L)))
    intercept[RuntimeException](TimedArenaRunner.parsePresets("3+x"))
  }

  // ---- Time-controlled arena: thin wall-clock smoke tests ----

  test("simulateTimedGame: a time-budgeted bot on a near-zero clock flags and loses on time") {
    val result =
      BotMatchRunner.simulateTimedGame(
        MonteCarloSearch,
        GreedySearch,
        new Random(1),
        new Random(2),
        TimeControl(5L, 0L)
      )
    assertEquals(result.flaggedColor, Some(Color.White)) // Monte-Carlo (White) overruns its 5ms and flags
    assertEquals(result.outcome, GameOutcome.Win(Color.Black))
    assertEquals(result.botLatenciesMs.size, 1) // exactly one timed move was made before the flag
  }

  test("runTimedMatch: O(1) bots never flag and the W/L/D totals are consistent") {
    val r = BotMatchRunner.runTimedMatch("greedy", "random", 2, TimeControl.ofSeconds(6, 0))
    assertEquals(r.totalGames, 4)
    assertEquals(r.wins + r.losses + r.draws, 4)
    assertEquals(r.botTimeouts, 0)
    assertEquals(r.baselineTimeouts, 0)
    assertEquals(r.latency, LatencyStats.empty) // neither bot is time-budgeted, so no latency samples
  }

  test("TimedArenaRunner.main: runs a small matrix without error") {
    TimedArenaRunner.main(Array("greedy", "random", "1", "6+0"))
  }
