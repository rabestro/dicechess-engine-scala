package dicechess.engine.bench

import dicechess.engine.domain.*
import dicechess.engine.search.*
import munit.FunSuite
import scala.util.Random

class BotMatchRunnerSpec extends FunSuite:

  test("simulateGame completes successfully and returns a valid outcome") {
    val outcome = BotMatchRunner.simulateGame(GreedySearch, GreedySearch, new Random(42), new Random(1000))

    // Outcome must be either a Win or a Draw
    assert(outcome == GameOutcome.Draw || outcome.isInstanceOf[GameOutcome.Win])
  }

  test("runMatch is reproducible across runs (bot tie-breaking is seeded, not fresh per move)") {
    def counts(r: MatchResult) =
      (r.winsAsWhite, r.winsAsBlack, r.lossesAsWhite, r.lossesAsBlack, r.drawsAsWhite, r.drawsAsBlack)
    // Both bots break ties randomly; only a seeded bot source makes two runs identical (this fails before the fix).
    val a = BotMatchRunner.runMatch(GreedySearch, AggressiveSearch, 4)
    val b = BotMatchRunner.runMatch(GreedySearch, AggressiveSearch, 4)
    assertEquals(counts(a), counts(b))
    // The hang telemetry is derived from the same seeded games, so it must be exactly as reproducible.
    assertEquals(a.opponentHangs, b.opponentHangs)
    assertEquals(a.baseHangs, b.baseHangs)
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

  // ---- Hang telemetry (#494) ----

  test("hang telemetry: per-algorithm stats satisfy their structural invariants") {
    val r = BotMatchRunner.runMatch(GreedySearch, AggressiveSearch, 3)
    for s <- List(r.opponentHangs, r.baseHangs) do
      assert(s.turns > 0, "every played game contributes completed turns")
      assert(s.hangTurns >= 0 && s.hangTurns <= s.turns)
      assert(s.queenHangTurns <= s.hangTurns, "a queen hang is a hang turn")
      assert(s.hangingMaterial >= s.hangTurns * 100L, "every hang turn contributes at least one pawn of material")
      assert(s.punishedMaterial >= s.punishedCaptures * 100L)
  }

  /** Plays a fixed script of turns, then passes forever — [[BotMatchRunner.playTurn]] applies moves without consulting
    * the dice, so a scripted game can pin the telemetry's cause-and-effect exactly.
    */
  final private class ScriptedBot(script: List[List[Move]]) extends SearchAlgorithm:
    private var remaining                                               = script
    override def findBestMove(state: GameState): Option[ScoredSequence] =
      remaining match
        case head :: tail =>
          remaining = tail
          Some(ScoredSequence(head, 0))
        case Nil => None

  test("hang telemetry: a hung queen and its punishment are attributed to the side that hung it") {
    // White walks its queen from d4 into the c6-pawn's attack (undefended), Black takes it next turn. Both sides then
    // shuffle their kings until the 50-move rule ends the game (passes would freeze the half-move clock forever) —
    // bare-king shuffling neither hangs anything nor captures anything, so the telemetry stays pinned to the script.
    def shuffle(file1: Char, file2: Char, rank: Int) = List.tabulate(60) { i =>
      if i % 2 == 0 then List(Move(Square(file1, rank), Square(file2, rank)))
      else List(Move(Square(file2, rank), Square(file1, rank)))
    }
    val start = FenParser.parse("4k3/8/2p5/8/3Q4/8/8/4K3 w - - 0 1").toOption.get
    val white = new ScriptedBot(List(Move(Square('d', 4), Square('d', 5))) :: shuffle('e', 'd', 1))
    val black = new ScriptedBot(List(Move(Square('c', 6), Square('d', 5))) :: shuffle('e', 'd', 8))

    val whiteTally = new HangTally
    val blackTally = new HangTally
    val outcome    =
      BotMatchRunner.simulateGame(white, black, new Random(42), new Random(1000), start, whiteTally, blackTally)
    assertEquals(outcome, GameOutcome.Draw) // both bots pass after the script, so the 50-move rule fires

    val w = whiteTally.snapshot
    assertEquals(w.hangTurns, 1)
    assertEquals(w.queenHangTurns, 1)
    assertEquals(w.hangingMaterial, 900L)
    assertEquals(w.punishedCaptures, 1)
    assertEquals(w.punishedMaterial, 900L)

    // Black never left anything en prise and was never punished; the c6→d5 pawn is not attacked by the bare king.
    val b = blackTally.snapshot
    assertEquals(b.hangTurns, 0)
    assertEquals(b.queenHangTurns, 0)
    assertEquals(b.hangingMaterial, 0L)
    assertEquals(b.punishedCaptures, 0)
    assertEquals(b.punishedMaterial, 0L)
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

  test("parsePresets: parses chess-clock 'minutes[+incrementSeconds]' specs and rejects bad input") {
    // 1-minute bullet, 3 min + 2 s, 10 min + 10 s — base is minutes, increment is seconds.
    assertEquals(
      TimedArenaRunner.parsePresets("1+0,3+2,10+10"),
      List(TimeControl(60000L, 0L), TimeControl(180000L, 2000L), TimeControl(600000L, 10000L))
    )
    assertEquals(TimedArenaRunner.parsePresets("1"), List(TimeControl(60000L, 0L)))
    intercept[RuntimeException](TimedArenaRunner.parsePresets("3+x")) // non-integer increment
    intercept[RuntimeException](TimedArenaRunner.parsePresets("0+2")) // base minutes must be > 0
    intercept[RuntimeException](TimedArenaRunner.parsePresets(""))    // at least one preset required
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
    assertEquals(result.latenciesByColorMs.size, 1)              // exactly one timed move was made before the flag
    assertEquals(result.latenciesByColorMs.head._1, Color.White) // and it is attributed to White (Monte-Carlo)
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
