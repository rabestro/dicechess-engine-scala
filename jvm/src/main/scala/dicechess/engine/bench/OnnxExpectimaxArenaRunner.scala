package dicechess.engine.bench

import dicechess.engine.domain.{Color, GameState}
import dicechess.engine.search.{
  BotInfo,
  BotRegistry,
  ExpectimaxConfig,
  OnnxExpectimaxSearch,
  OnnxFeatures,
  RichFeatures
}

/** Acceptance-gate arena for the 2-ply ONNX expectimax bot against a built-in baseline (`aggressive` by default) — the
  * ">= 55% win rate" check for the Dice Chess AI hackathon project.
  *
  * Same orientation as [[OnnxArenaRunner]]: the built-in bot is the [[BotMatchRunner.runArena]] baseline and the
  * expectimax bot is the measured opponent, so the printed row is the model bot's win/loss/win-rate. The comparison
  * against [[OnnxArenaRunner]] (one-ply, same model) shows what the lookahead buys.
  *
  * The model file is read from a runtime path and never committed to this public repository.
  *
  * Usage: `runMain dicechess.engine.bench.OnnxExpectimaxArenaRunner <modelPath> [opponentBotId] [gamesPerColor]
  * [candidateLimit] [features]`, where `features` is `material` (default, the 7-feature [[OnnxFeatures]] model) or
  * `rich` (the 9-feature [[RichFeatures]] model — must match how the model at `modelPath` was trained).
  */
object OnnxExpectimaxArenaRunner:

  private val StartFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  def main(args: Array[String]): Unit =
    val modelPath = args.headOption.getOrElse(
      sys.error(
        "Usage: OnnxExpectimaxArenaRunner <modelPath> [opponentBotId] [gamesPerColor] [candidateLimit] [features]"
      )
    )
    val opponentId     = args.lift(1).getOrElse("aggressive")
    val games          = args.lift(2).flatMap(_.toIntOption).getOrElse(200)
    val candidateLimit = args.lift(3).flatMap(_.toIntOption).getOrElse(ExpectimaxConfig().candidateLimit)

    val featureSet                                          = args.lift(4).getOrElse("material")
    val extractFeatures: (GameState, Color) => Array[Float] = featureSet.toLowerCase match
      case "material" => OnnxFeatures.extract
      case "rich"     => RichFeatures.extract
      case other      => sys.error(s"Unknown feature set '$other' (expected 'material' or 'rich')")

    val opponentInfo = BotRegistry.availableBots
      .find(_.id.equalsIgnoreCase(opponentId))
      .getOrElse(sys.error(s"Unknown opponent bot '$opponentId'"))

    val botId = "onnx-expectimax"
    val bot   = new OnnxExpectimaxSearch(modelPath, ExpectimaxConfig(candidateLimit), extractFeatures)
    try
      BotRegistry.registerCustomBot(
        BotInfo(
          id = botId,
          name = "ONNX Expectimax (2-ply)",
          description = s"2-ply expectimax scored by an externally-trained LightGBM model ($modelPath)",
          difficulty = opponentInfo.difficulty,
          isExperimental = true
        ),
        bot
      )

      println(s"Loaded ONNX model from $modelPath (candidateLimit=$candidateLimit, features=$featureSet)")
      // Opponent as baseline, expectimax bot as the measured side: the table row is the model bot's stats.
      BotMatchRunner.runArena(opponentInfo.id, Some(botId), games, StartFen)
    finally bot.close()
