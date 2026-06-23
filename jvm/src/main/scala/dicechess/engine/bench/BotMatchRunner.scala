package dicechess.engine.bench

import dicechess.engine.domain.*
import dicechess.engine.search.*
import scala.util.Random

/** Executable task that runs bot-vs-bot matches in memory.
  *
  * Simulates matches between different search algorithms to evaluate their playing strength. Measures wins, losses,
  * draws, and computes win rates relative to a baseline bot.
  */
object BotMatchRunner:

  private val StartFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  def main(args: Array[String]): Unit =
    val baseBotId     = args.headOption.getOrElse("greedy")
    val gamesPerColor = args.lift(1).flatMap(_.toIntOption).getOrElse(50)

    try runArena(baseBotId, None, gamesPerColor, StartFen)
    catch
      case e: Exception =>
        System.err.println(e.getMessage)
        sys.exit(1)

  def runArena(baseBotId: String, opponentBotId: Option[String], gamesPerColor: Int, startFen: String): Unit =
    if gamesPerColor <= 0 then sys.error(s"Invalid gamesPerColor '$gamesPerColor'. Must be greater than 0.")

    val parsedFen =
      FenParser.parse(startFen).getOrElse(sys.error(s"Invalid start FEN: $startFen"))

    val baseAlgorithmOpt = BotRegistry.getAlgorithm(baseBotId)
    if baseAlgorithmOpt.isEmpty then sys.error(s"Baseline bot with ID '$baseBotId' not found in BotRegistry!")

    val baseAlgorithm = baseAlgorithmOpt.get
    val baseBotIdNorm = baseBotId.toLowerCase
    val baseBotInfo   = BotRegistry.availableBots
      .find(_.id.toLowerCase == baseBotIdNorm)
      .getOrElse {
        sys.error(
          s"Baseline bot details with ID '$baseBotId' not found in BotRegistry!"
        )
      }

    println("================================================================================")
    println(s"🎲♟️  Dice Chess Bot Arena - JVM Match Runner")
    println(s"Baseline Bot: ${baseBotInfo.name} (${baseBotInfo.id})")
    println(s"Games per Color: $gamesPerColor (Total ${gamesPerColor * 2} games per match)")
    if startFen != StartFen then println(s"Starting FEN: $startFen")
    println("================================================================================")

    val opponents = opponentBotId match
      case Some(id) =>
        BotRegistry.availableBots
          .find(_.id.toLowerCase == id.toLowerCase)
          .map(List(_))
          .getOrElse(sys.error(s"Opponent bot with ID '$id' not found!"))
      case None => BotRegistry.availableBots

    val results = for opponentInfo <- opponents yield
      val opponentAlgo = BotRegistry.getAlgorithm(opponentInfo.id).get
      val matchResult  = runMatch(opponentAlgo, baseAlgorithm, gamesPerColor, parsedFen)
      (opponentInfo, matchResult)

    printSummaryTable(results)

  /** Package-private visibility (`private[bench]`) is utilized to expose match orchestration to [[BotMatchRunnerSpec]]
    * for verification of win rates and results aggregation, while keeping execution internal to the bench module.
    */
  private[bench] def runMatch(
      opponentAlgo: SearchAlgorithm,
      baseAlgo: SearchAlgorithm,
      gamesPerColor: Int,
      startFen: GameState = FenParser.parse(StartFen).toOption.get
  ): MatchResult =
    val rand          = new Random(42) // Fixed seed for reproducible run results
    var winsAsWhite   = 0
    var winsAsBlack   = 0
    var lossesAsWhite = 0
    var lossesAsBlack = 0
    var drawsAsWhite  = 0
    var drawsAsBlack  = 0

    val startTime = System.currentTimeMillis()

    // 1. Play games with Opponent as White and Base Bot as Black
    for _ <- 1 to gamesPerColor do
      simulateGame(opponentAlgo, baseAlgo, rand, startFen) match
        case GameOutcome.Win(color) =>
          if color.isWhite then winsAsWhite += 1 else lossesAsWhite += 1
        case GameOutcome.Draw =>
          drawsAsWhite += 1

    // 2. Play games with Base Bot as White and Opponent as Black
    for _ <- 1 to gamesPerColor do
      simulateGame(baseAlgo, opponentAlgo, rand, startFen) match
        case GameOutcome.Win(color) =>
          if color.isBlack then winsAsBlack += 1 else lossesAsBlack += 1
        case GameOutcome.Draw =>
          drawsAsBlack += 1

    val durationMs = System.currentTimeMillis() - startTime
    MatchResult(
      totalGames = gamesPerColor * 2,
      winsAsWhite = winsAsWhite,
      winsAsBlack = winsAsBlack,
      lossesAsWhite = lossesAsWhite,
      lossesAsBlack = lossesAsBlack,
      drawsAsWhite = drawsAsWhite,
      drawsAsBlack = drawsAsBlack,
      durationMs = durationMs
    )

  /** Package-private visibility (`private[bench]`) allows [[BotMatchRunnerSpec]] to verify individual turn executions,
    * random seed reproducibility, and the 50-move rule draw condition.
    */
  private[bench] def simulateGame(
      whiteBot: SearchAlgorithm,
      blackBot: SearchAlgorithm,
      rand: Random,
      startState: GameState = FenParser.parse(StartFen).toOption.get
  ): GameOutcome =
    var state                = startState
    var isGameOver           = false
    var outcome: GameOutcome = GameOutcome.Draw

    while !isGameOver do
      if state.halfMoveClock >= 100 then
        isGameOver = true
        outcome = GameOutcome.Draw
      else
        // Roll 3 random dice
        val dice           = List.fill(3)(rand.nextInt(6) + 1)
        val stateWithDice  = state.withDicePool(dice)
        val activeBot      = if state.activeColor.isWhite then whiteBot else blackBot
        val (next, winner) = playTurn(state, activeBot.findBestMove(stateWithDice))
        winner match
          case Some(color) =>
            outcome = GameOutcome.Win(color)
            isGameOver = true
          case None =>
            state = next

    outcome

  /** Applies one bot turn to `state`, preserving the active color across the 1–3 micro-moves (Dice Chess rule).
    *
    * A `None` `scoredSeq` is a forced pass. Returns the resulting state and the winner when a King is captured (in
    * which case the turn is *not* ended, mirroring the engine's terminal handling). Shared by the untimed
    * [[simulateGame]] and the timed [[simulateTimedGame]] so move application and desync checks live in one place.
    */
  private[bench] def playTurn(state: GameState, scoredSeq: Option[ScoredSequence]): (GameState, Option[Color]) =
    scoredSeq match
      case None =>
        val next = state.endTurn()
        verifySync(next, "endTurn(pass)")
        (next, None)
      case Some(seq) =>
        var tempState             = state
        var winner: Option[Color] = None
        val iterator              = seq.moves.iterator
        while iterator.hasNext && winner.isEmpty do
          val m      = iterator.next()
          val target = tempState.mailbox(m.toSquare)
          if !target.isEmpty && target.pieceType == PieceType.King && target.color != tempState.activeColor then
            winner = Some(tempState.activeColor)
          tempState = tempState.makeMove(m)
          verifySync(tempState, s"${m.fromSquare.toNotation}${m.toSquare.toNotation}")
        if winner.isDefined then (tempState, winner)
        else
          val next = tempState.endTurn()
          verifySync(next, "endTurn")
          (next, None)

  /** In-process slack subtracted from the [[TimeManager]] budget: one uninterruptible rollout can overrun the deadline,
    * and this keeps that overrun off the simulated clock. Smaller than the JS API's worker buffer (no postMessage
    * here).
    */
  private val ArenaOverheadBufferMs: Long = 50L

  /** Deducts `elapsedMs` from a side's clock and, unless the side flagged, credits the Fischer `incrementMs`.
    *
    * @return
    *   the updated remaining time and whether the side ran out of time on this turn (flag-fall)
    */
  private[bench] def tickClock(remainingMs: Long, elapsedMs: Long, incrementMs: Long): (Long, Boolean) =
    val afterSpend = remainingMs - elapsedMs
    if afterSpend < 0 then (afterSpend, true)
    else (afterSpend + incrementMs, false)

  /** Plays a single game under a wall-clock [[TimeControl]].
    *
    * Each side starts with `tc.initialMs`. A [[TimeBudgetedSearch]] bot is given a per-turn deadline derived from
    * [[TimeManager]] and the side's remaining clock; O(1) bots simply move (their elapsed time is still charged, but is
    * negligible). After each turn the elapsed wall-clock is deducted and the increment credited; a side whose clock
    * goes negative loses on time. Non-deterministic by nature (depends on machine speed) — for measurement, not
    * reproducible assertions.
    *
    * @param diceRandom
    *   source for the dice rolls
    * @param botRandom
    *   source handed to the time-budgeted search, kept separate so a varying rollout count never perturbs the dice
    */
  private[bench] def simulateTimedGame(
      whiteBot: SearchAlgorithm,
      blackBot: SearchAlgorithm,
      diceRandom: Random,
      botRandom: Random,
      tc: TimeControl,
      startState: GameState = FenParser.parse(StartFen).toOption.get
  ): TimedGameResult =
    var state                           = startState
    var whiteRemaining                  = tc.initialMs
    var blackRemaining                  = tc.initialMs
    val latencies                       = scala.collection.mutable.ListBuffer.empty[Long]
    var result: Option[TimedGameResult] = None

    while result.isEmpty do
      if state.halfMoveClock >= 100 then result = Some(TimedGameResult(GameOutcome.Draw, None, latencies.toList))
      else
        val dice          = List.fill(3)(diceRandom.nextInt(6) + 1)
        val stateWithDice = state.withDicePool(dice)
        val mover         = state.activeColor
        val isWhite       = mover.isWhite
        val activeBot     = if isWhite then whiteBot else blackBot
        val remaining     = if isWhite then whiteRemaining else blackRemaining

        val timed = activeBot match
          case tb: TimeBudgetedSearch => Some(tb)
          case _                      => None

        val startNanos = System.nanoTime()
        val scored     = timed match
          case Some(tb) =>
            val budgetMs =
              TimeManager.budgetMs(ClockState(remaining, tc.incrementMs, state.fullMoveNumber), ArenaOverheadBufferMs)
            tb.findBestMove(stateWithDice, startNanos + budgetMs * 1_000_000L, botRandom)
          case None => activeBot.findBestMove(stateWithDice)
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L

        if timed.isDefined then latencies += elapsedMs

        val (newRemaining, flagged) = tickClock(remaining, elapsedMs, tc.incrementMs)
        if isWhite then whiteRemaining = newRemaining else blackRemaining = newRemaining

        if flagged then result = Some(TimedGameResult(GameOutcome.Win(mover.opponent), Some(mover), latencies.toList))
        else
          val (next, winner) = playTurn(state, scored)
          winner match
            case Some(color) => result = Some(TimedGameResult(GameOutcome.Win(color), None, latencies.toList))
            case None        => state = next

    result.get

  /** Runs a time-controlled match: `botUnderTestId` vs `baselineId`, `gamesPerColor` games on each side, under `tc`.
    *
    * Results are reported from the bot-under-test's perspective (its wins/losses/draws, the games it lost on time, and
    * the latency distribution of its own moves), which is what the #372 gate asks for.
    *
    * The latency distribution aggregates every [[TimeBudgetedSearch]] move, so it is unambiguous only when the baseline
    * is an O(1) bot (the gate setup, e.g. Aggressive); a timed-vs-timed match would blend both sides' think times.
    */
  private[bench] def runTimedMatch(
      botUnderTestId: String,
      baselineId: String,
      gamesPerColor: Int,
      tc: TimeControl,
      startState: GameState = FenParser.parse(StartFen).toOption.get
  ): TimedMatchResult =
    val botAlgo  = BotRegistry.getAlgorithm(botUnderTestId).getOrElse(sys.error(s"Bot '$botUnderTestId' not found"))
    val baseAlgo = BotRegistry.getAlgorithm(baselineId).getOrElse(sys.error(s"Bot '$baselineId' not found"))

    var wins             = 0
    var losses           = 0
    var draws            = 0
    var botTimeouts      = 0 // games the bot under test lost on time
    var baselineTimeouts = 0
    val latencies        = scala.collection.mutable.ListBuffer.empty[Long]
    val startTime        = System.currentTimeMillis()

    def record(res: TimedGameResult, botColor: Color): Unit =
      latencies ++= res.botLatenciesMs
      res.outcome match
        case GameOutcome.Draw                    => draws += 1
        case GameOutcome.Win(c) if c == botColor => wins += 1
        case GameOutcome.Win(_)                  => losses += 1
      res.flaggedColor.foreach { flagged =>
        if flagged == botColor then botTimeouts += 1 else baselineTimeouts += 1
      }

    for i <- 0 until gamesPerColor do
      record(
        simulateTimedGame(botAlgo, baseAlgo, new Random(42 + i), new Random(1000 + i), tc, startState),
        Color.White
      )
    for i <- 0 until gamesPerColor do
      record(
        simulateTimedGame(baseAlgo, botAlgo, new Random(42 + i), new Random(2000 + i), tc, startState),
        Color.Black
      )

    TimedMatchResult(
      timeControl = tc,
      totalGames = gamesPerColor * 2,
      wins = wins,
      losses = losses,
      draws = draws,
      botTimeouts = botTimeouts,
      baselineTimeouts = baselineTimeouts,
      latency = LatencyStats.from(latencies.toList),
      durationMs = System.currentTimeMillis() - startTime
    )

  private[bench] def printTimedSummary(botId: String, baselineId: String, results: List[TimedMatchResult]): Unit =
    println("================================================================================")
    println(s"🎲♟️  Time-Controlled Arena — $botId (bot under test) vs $baselineId")
    println("================================================================================")
    println(
      f"${"Control"}%-10s | ${"Games"}%-5s | ${"Score"}%-7s | ${"W/L/D"}%-12s | ${"Timeout b/o"}%-12s | ${"p50/p95/p99/max ms"}%-24s | ${"Wall"}%-8s"
    )
    println("-" * 100)
    for r <- results do
      val wld  = s"${r.wins}/${r.losses}/${r.draws}"
      val to   = s"${r.botTimeouts}/${r.baselineTimeouts}"
      val lat  = s"${r.latency.p50Ms}/${r.latency.p95Ms}/${r.latency.p99Ms}/${r.latency.maxMs}"
      val wall = s"${"%.1f".format(r.durationMs / 1000.0)}s"
      println(
        f"${r.timeControl.label}%-10s | ${r.totalGames}%-5d | ${r.scorePercent}%6.1f%% | $wld%-12s | $to%-12s | $lat%-24s | $wall"
      )
    println("================================================================================\n")

  private val enableVerifySync =
    sys.props.get("dicechess.bench.verifySync").flatMap(_.toBooleanOption).getOrElse(false)

  private def verifySync(state: GameState, lastMove: String): Unit =
    if enableVerifySync then verifySyncInternal(state, lastMove)

  private def verifySyncInternal(state: GameState, lastMove: String): Unit =
    for i <- 0 until 64 do
      val sq    = Square.fromIndex(i)
      val piece = state.mailbox(sq)
      if piece.isEmpty then
        if state.whitePieces.contains(sq) then
          sys.error(
            s"Desync: mailbox empty but whitePieces set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.blackPieces.contains(sq) then
          sys.error(
            s"Desync: mailbox empty but blackPieces set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.pawns.contains(sq) then
          sys.error(
            s"Desync: mailbox empty but pawns set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.knights.contains(sq) then
          sys.error(
            s"Desync: mailbox empty but knights set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.bishops.contains(sq) then
          sys.error(
            s"Desync: mailbox empty but bishops set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.rooks.contains(sq) then
          sys.error(
            s"Desync: mailbox empty but rooks set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.queens.contains(sq) then
          sys.error(
            s"Desync: mailbox empty but queens set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.kings.contains(sq) then
          sys.error(
            s"Desync: mailbox empty but kings set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
      else
        val color = piece.color
        val pt    = piece.pieceType
        if color.isWhite then
          if !state.whitePieces.contains(sq) then
            sys.error(
              s"Desync: mailbox has white $pt but whitePieces not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
            )
          if state.blackPieces.contains(sq) then
            sys.error(
              s"Desync: mailbox has white $pt but blackPieces set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
            )
        else
          if !state.blackPieces.contains(sq) then
            sys.error(
              s"Desync: mailbox has black $pt but blackPieces not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
            )
          if state.whitePieces.contains(sq) then
            sys.error(
              s"Desync: mailbox has black $pt but whitePieces set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
            )

        if pt == PieceType.Pawn && !state.pawns.contains(sq) then
          sys.error(
            s"Desync: mailbox has Pawn but pawns not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt == PieceType.Knight && !state.knights.contains(sq) then
          sys.error(
            s"Desync: mailbox has Knight but knights not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt == PieceType.Bishop && !state.bishops.contains(sq) then
          sys.error(
            s"Desync: mailbox has Bishop but bishops not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt == PieceType.Rook && !state.rooks.contains(sq) then
          sys.error(
            s"Desync: mailbox has Rook but rooks not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt == PieceType.Queen && !state.queens.contains(sq) then
          sys.error(
            s"Desync: mailbox has Queen but queens not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt == PieceType.King && !state.kings.contains(sq) then
          sys.error(
            s"Desync: mailbox has King but kings not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )

        if pt != PieceType.Pawn && state.pawns.contains(sq) then
          sys.error(
            s"Desync: mailbox has $pt but pawns set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt != PieceType.Knight && state.knights.contains(sq) then
          sys.error(
            s"Desync: mailbox has $pt but knights set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt != PieceType.Bishop && state.bishops.contains(sq) then
          sys.error(
            s"Desync: mailbox has $pt but bishops set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt != PieceType.Rook && state.rooks.contains(sq) then
          sys.error(
            s"Desync: mailbox has $pt but rooks set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt != PieceType.Queen && state.queens.contains(sq) then
          sys.error(
            s"Desync: mailbox has $pt but queens set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt != PieceType.King && state.kings.contains(sq) then
          sys.error(
            s"Desync: mailbox has $pt but kings set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )

  private def printSummaryTable(results: List[(BotInfo, MatchResult)]): Unit =
    println("\n📊 MATCH RESULTS OVERVIEW:")
    println(
      f"${"Opponent Bot"}%-20s | ${"Total"}%-5s | ${"Wins (W/B)"}%-12s | ${"Losses (W/B)"}%-12s | ${"Draws (W/B)"}%-12s | ${"Win Rate"}%-8s | ${"Time"}%-8s"
    )
    println("-" * 92)

    for (botInfo, r) <- results do
      val totalWins   = r.winsAsWhite + r.winsAsBlack
      val totalLosses = r.lossesAsWhite + r.lossesAsBlack
      val totalDraws  = r.drawsAsWhite + r.drawsAsBlack
      val winRate     = (totalWins.toDouble + 0.5 * totalDraws) / r.totalGames * 100.0
      val timeStr     = s"${"%.2f".format(r.durationMs / 1000.0)}s"

      val winsStr   = s"$totalWins (${r.winsAsWhite}/${r.winsAsBlack})"
      val lossesStr = s"$totalLosses (${r.lossesAsWhite}/${r.lossesAsBlack})"
      val drawsStr  = s"$totalDraws (${r.drawsAsWhite}/${r.drawsAsBlack})"

      println(
        f"${botInfo.name}%-20s | ${r.totalGames}%-5d | $winsStr%-12s | $lossesStr%-12s | $drawsStr%-12s | $winRate%6.1f%% | $timeStr"
      )
    println("================================================================================\n")

case class MatchResult(
    totalGames: Int,
    winsAsWhite: Int,
    winsAsBlack: Int,
    lossesAsWhite: Int,
    lossesAsBlack: Int,
    drawsAsWhite: Int,
    drawsAsBlack: Int,
    durationMs: Long
)

enum GameOutcome derives CanEqual:
  case Win(color: Color)
  case Draw

/** A wall-clock time control: an initial budget plus a per-turn Fischer increment, both in milliseconds. */
final case class TimeControl(initialMs: Long, incrementMs: Long):
  /** Compact label such as `"60s"` (sudden death) or `"180s+2s"` (Fischer). */
  def label: String =
    val base = initialMs / 1000
    if incrementMs == 0 then s"${base}s" else s"${base}s+${incrementMs / 1000}s"

object TimeControl:
  /** Builds a control from whole seconds, e.g. `ofSeconds(60, 0)` or `ofSeconds(180, 2)`. */
  def ofSeconds(initialSec: Int, incrementSec: Int): TimeControl =
    TimeControl(initialSec * 1000L, incrementSec * 1000L)

/** Outcome of one timed game, plus the side that flagged (if any) and the timed bot's per-turn think times. */
final case class TimedGameResult(outcome: GameOutcome, flaggedColor: Option[Color], botLatenciesMs: List[Long])

/** Nearest-rank latency percentiles (ms) over a set of move think times. */
final case class LatencyStats(count: Int, p50Ms: Long, p95Ms: Long, p99Ms: Long, maxMs: Long)

object LatencyStats:
  val empty: LatencyStats = LatencyStats(0, 0L, 0L, 0L, 0L)

  /** Nearest-rank percentiles over `samples`; returns [[empty]] for no samples. */
  def from(samples: Seq[Long]): LatencyStats =
    if samples.isEmpty then empty
    else
      val sorted                   = samples.sorted.toVector
      def percentile(p: Int): Long =
        val rank = math.ceil(p / 100.0 * sorted.size).toInt
        sorted(math.min(sorted.size, math.max(1, rank)) - 1)
      LatencyStats(sorted.size, percentile(50), percentile(95), percentile(99), sorted.last)

/** Aggregated result of a time-controlled match, from the bot-under-test's perspective. */
final case class TimedMatchResult(
    timeControl: TimeControl,
    totalGames: Int,
    wins: Int,
    losses: Int,
    draws: Int,
    botTimeouts: Int,
    baselineTimeouts: Int,
    latency: LatencyStats,
    durationMs: Long
):
  /** Win-rate of the bot under test, counting draws as half a point. */
  def scorePercent: Double = (wins + 0.5 * draws) / totalGames * 100.0
