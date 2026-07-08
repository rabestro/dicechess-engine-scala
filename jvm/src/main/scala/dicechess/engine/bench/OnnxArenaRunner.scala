package dicechess.engine.bench

import dicechess.engine.search.{BotInfo, BotRegistry, OnnxEvalSearch}

/** Local arena between an externally-trained (LightGBM, via ONNX) one-ply evaluator and a built-in bot — the
  * acceptance-gate check for the Dice Chess AI hackathon project (>= 55% win rate over enough games to be a real
  * signal).
  *
  * The built-in bot is passed to [[BotMatchRunner.runArena]] as the BASELINE and the ONNX bot as the opponent, because
  * the summary table reports each opponent's wins against the baseline — this way the printed row is the MODEL's
  * win/loss/win-rate, which is the number the gate is about (same orientation as [[OpeningBookArenaRunner]]).
  *
  * The ONNX model file is read from a path at runtime, so it never has to be committed to this public repository — keep
  * it outside version control (it lives in a private repository's `models/` directory, not published alongside this
  * codebase).
  *
  * Usage: `runMain dicechess.engine.bench.OnnxArenaRunner <modelPath> [opponentBotId] [gamesPerColor]` (or
  * `mise run arena:onnx <modelPath> [opponentBotId] [games]`).
  */
object OnnxArenaRunner:

  private val StartFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  def main(args: Array[String]): Unit =
    val modelPath = args.headOption.getOrElse(
      sys.error("Usage: OnnxArenaRunner <modelPath> [opponentBotId] [gamesPerColor]")
    )
    val opponentId = args.lift(1).getOrElse("aggressive")
    val games      = args.lift(2).flatMap(_.toIntOption).getOrElse(200)

    val opponentInfo = BotRegistry.availableBots
      .find(_.id.equalsIgnoreCase(opponentId))
      .getOrElse(sys.error(s"Unknown opponent bot '$opponentId'"))

    val onnxId = "onnx-eval"
    val bot    = new OnnxEvalSearch(modelPath)
    try
      BotRegistry.registerCustomBot(
        BotInfo(
          id = onnxId,
          name = "ONNX LightGBM Eval",
          description = s"One-ply search scored by an externally-trained LightGBM model ($modelPath)",
          difficulty = opponentInfo.difficulty,
          isExperimental = true
        ),
        bot
      )

      println(s"Loaded ONNX model from $modelPath")
      // Opponent as baseline, ONNX bot as the measured side: the table row is the model's stats.
      BotMatchRunner.runArena(opponentInfo.id, Some(onnxId), games, StartFen)
    finally bot.close()
