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

    if gamesPerColor <= 0 then
      System.err.println(s"Error: Invalid gamesPerColor '$gamesPerColor'. Must be greater than 0.")
      sys.exit(1)

    val baseAlgorithmOpt = BotRegistry.getAlgorithm(baseBotId)
    if baseAlgorithmOpt.isEmpty then
      System.err.println(s"Error: Baseline bot with ID '$baseBotId' not found in BotRegistry!")
      sys.exit(1)

    val baseAlgorithm = baseAlgorithmOpt.get
    val baseBotIdNorm = baseBotId.toLowerCase
    val baseBotInfo   = BotRegistry.availableBots
      .find(_.id.toLowerCase == baseBotIdNorm)
      .getOrElse {
        System.err.println(s"Error: Baseline bot details with ID '$baseBotId' not found in BotRegistry!")
        sys.exit(1)
      }

    println("================================================================================")
    println(s"🎲♟️  Dice Chess Bot Arena - JVM Match Runner")
    println(s"Baseline Bot: ${baseBotInfo.name} (${baseBotInfo.id})")
    println(s"Games per Color: $gamesPerColor (Total ${gamesPerColor * 2} games per match)")
    println("================================================================================")

    val opponents = BotRegistry.availableBots

    val results = for opponentInfo <- opponents yield
      val opponentAlgo = BotRegistry.getAlgorithm(opponentInfo.id).get
      val matchResult  = runMatch(opponentAlgo, baseAlgorithm, gamesPerColor)
      (opponentInfo, matchResult)

    printSummaryTable(results)

  /** Package-private visibility (`private[bench]`) is utilized to expose match orchestration to [[BotMatchRunnerSpec]]
    * for verification of win rates and results aggregation, while keeping execution internal to the bench module.
    */
  private[bench] def runMatch(
      opponentAlgo: SearchAlgorithm,
      baseAlgo: SearchAlgorithm,
      gamesPerColor: Int
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
    for (_ <- 1 to gamesPerColor) do
      simulateGame(opponentAlgo, baseAlgo, rand) match
        case GameOutcome.Win(color) =>
          if color.isWhite then winsAsWhite += 1 else lossesAsWhite += 1
        case GameOutcome.Draw =>
          drawsAsWhite += 1

    // 2. Play games with Base Bot as White and Opponent as Black
    for (_ <- 1 to gamesPerColor) do
      simulateGame(baseAlgo, opponentAlgo, rand) match
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
      rand: Random
  ): GameOutcome =
    var state                = FenParser.parse(StartFen).getOrElse(throw new Exception("Start FEN is invalid!"))
    var isGameOver           = false
    var outcome: GameOutcome = GameOutcome.Draw

    while !isGameOver do
      if state.halfMoveClock >= 100 then
        isGameOver = true
        outcome = GameOutcome.Draw
      else
        // Roll 3 random dice
        val dice          = List.fill(3)(rand.nextInt(6) + 1)
        val stateWithDice = state.withDicePool(dice)
        val activeBot     = if state.activeColor.isWhite then whiteBot else blackBot

        activeBot.findBestMove(stateWithDice) match
          case None =>
            // Force turn-pass when no legal move exists
            state = state.endTurn()
            verifySync(state, "endTurn(pass)")
          case Some(scoredSeq) =>
            var tempState    = state
            var kingCaptured = false
            val moves        = scoredSeq.moves

            val iterator = moves.iterator
            while iterator.hasNext && !kingCaptured do
              val m      = iterator.next()
              val target = tempState.mailbox(m.toSquare)
              if !target.isEmpty && target.pieceType == PieceType.King && target.color != tempState.activeColor then
                kingCaptured = true
                outcome = GameOutcome.Win(tempState.activeColor)
                isGameOver = true
              tempState = tempState.makeMove(m)
              verifySync(tempState, s"${m.fromSquare.toNotation}${m.toSquare.toNotation}")

            if kingCaptured then state = tempState
            else
              state = tempState.endTurn()
              verifySync(state, "endTurn")

    outcome

  private val enableVerifySync =
    sys.props.getOrElse("dicechess.bench.verifySync", "false").toBooleanOption.getOrElse(false)

  private def verifySync(state: GameState, lastMove: String): Unit =
    if enableVerifySync then verifySyncInternal(state, lastMove)

  private def verifySyncInternal(state: GameState, lastMove: String): Unit =
    for i <- 0 until 64 do
      val sq    = Square.fromIndex(i)
      val piece = state.mailbox(sq)
      if piece.isEmpty then
        if state.whitePieces.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox empty but whitePieces set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.blackPieces.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox empty but blackPieces set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.pawns.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox empty but pawns set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.knights.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox empty but knights set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.bishops.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox empty but bishops set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.rooks.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox empty but rooks set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.queens.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox empty but queens set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if state.kings.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox empty but kings set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
      else
        val color = piece.color
        val pt    = piece.pieceType
        if color.isWhite then
          if !state.whitePieces.contains(sq) then
            throw new RuntimeException(
              s"Desync: mailbox has white $pt but whitePieces not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
            )
          if state.blackPieces.contains(sq) then
            throw new RuntimeException(
              s"Desync: mailbox has white $pt but blackPieces set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
            )
        else
          if !state.blackPieces.contains(sq) then
            throw new RuntimeException(
              s"Desync: mailbox has black $pt but blackPieces not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
            )
          if state.whitePieces.contains(sq) then
            throw new RuntimeException(
              s"Desync: mailbox has black $pt but whitePieces set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
            )

        if pt == PieceType.Pawn && !state.pawns.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox has Pawn but pawns not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt == PieceType.Knight && !state.knights.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox has Knight but knights not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt == PieceType.Bishop && !state.bishops.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox has Bishop but bishops not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt == PieceType.Rook && !state.rooks.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox has Rook but rooks not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt == PieceType.Queen && !state.queens.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox has Queen but queens not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt == PieceType.King && !state.kings.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox has King but kings not set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )

        if pt != PieceType.Pawn && state.pawns.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox has $pt but pawns set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt != PieceType.Knight && state.knights.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox has $pt but knights set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt != PieceType.Bishop && state.bishops.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox has $pt but bishops set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt != PieceType.Rook && state.rooks.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox has $pt but rooks set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt != PieceType.Queen && state.queens.contains(sq) then
          throw new RuntimeException(
            s"Desync: mailbox has $pt but queens set at ${sq.toNotation} (after $lastMove) in FEN: ${FenParser.serialize(state)}"
          )
        if pt != PieceType.King && state.kings.contains(sq) then
          throw new RuntimeException(
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

enum GameOutcome:
  case Win(color: Color)
  case Draw
