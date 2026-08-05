package dicechess.engine.bench

import dicechess.engine.domain.{Color, GameState}
import dicechess.engine.search.{
  BotInfo,
  BotRegistry,
  ExpectimaxConfig,
  KcpFeatures,
  OnnxExpectimaxSearch,
  OnnxFeatures,
  OpeningBookBot,
  OpeningBookParser,
  RawBoardFeatures,
  RichFeatures,
  RootRescoreModel,
  SearchAlgorithm
}

import scala.io.Source

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
  * [candidateLimit] [features] [rescoreModelPath] [rescoreWeight] [preRankWithModel] [bookPath]`, where `features`
  * selects the leaf extractor (must match `modelPath`'s trained model): `material` (default, 7-feature
  * [[OnnxFeatures]]) or `rich` (9-feature [[RichFeatures]]). `kcp` exists but is one-ply only at the leaves (see
  * [[OnnxArenaRunner]]) — its capture-probability columns are far too heavy under a chance node's hundreds of leaves.
  *
  * `rescoreModelPath`, when given (non-empty), wires a *second* model — always [[KcpFeatures]] (13-feature), the one
  * root rescoring is for — as the root-level rescorer blended in at `rescoreWeight` (default `0.5`); see
  * [[dicechess.engine.search.RootRescore]]. This is the affordable way to bring KCP's tactical signal into a live bot:
  * full leaf-level KCP is ~18x too slow (measured), but scoring only the handful of root candidates once is not.
  *
  * `preRankWithModel` (any of `true`/`1`/`model`, case-insensitive; default off), when set, pre-ranks root candidates
  * with `modelPath`'s own batched inference instead of material — no extra model, testing whether a sharper pre-ranker
  * reaches candidateLimit=16's strength (or better) at a smaller, cheaper K.
  *
  * `bookPath`, when given (non-empty), loads an opening book (the flat JSON of [[OpeningBookParser]], produced by the
  * analytics exporter) and wraps the bot with [[dicechess.engine.search.OpeningBookBot]] — booked positions play the
  * booked turn instantly, everything else falls through to the expectimax search. This is the seeded gate for the
  * combination the live fleet would actually run; like the book file itself, the path stays outside this public repo.
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
      case "kcp"      => KcpFeatures.extract
      case "rawboard" => RawBoardFeatures.extract
      case other      =>
        sys.error(s"Unknown feature set '$other' (expected 'material', 'rich', 'kcp', or 'rawboard')")

    val rescoreModelPath = args.lift(5).map(_.trim).filter(_.nonEmpty)
    val rescoreWeight    = args.lift(6).flatMap(_.toDoubleOption).getOrElse(0.5)
    val rootRescore      = rescoreModelPath.map(path => RootRescoreModel(path, KcpFeatures.extract, rescoreWeight))
    val preRankWithModel = args.lift(7).exists(flag => Set("true", "1", "model").contains(flag.trim.toLowerCase))
    val bookPath         = args.lift(8).map(_.trim).filter(_.nonEmpty)

    val opponentInfo = BotRegistry.availableBots
      .find(_.id.equalsIgnoreCase(opponentId))
      .getOrElse(sys.error(s"Unknown opponent bot '$opponentId'"))

    val botId = "onnx-expectimax"
    val bot   = new OnnxExpectimaxSearch(
      modelPath,
      ExpectimaxConfig(candidateLimit),
      extractFeatures,
      rootRescore,
      preRankWithModel
    )
    try
      // With a book, the *decorated* algorithm is registered while `bot` is kept for closing the
      // ONNX session — the decorator owns no resources of its own.
      val book = bookPath.map { path =>
        val json =
          val source = Source.fromFile(path)
          try source.mkString
          finally source.close()
        OpeningBookParser
          .parse(json)
          .fold(error => sys.error(s"Failed to parse opening book '$path': ${error.getMessage}"), identity)
      }
      val algorithm = book.fold(bot: SearchAlgorithm)(entries => OpeningBookBot.decorate(bot, entries))
      BotRegistry.registerCustomBot(
        BotInfo(
          id = botId,
          name = "ONNX Expectimax (2-ply)",
          description = s"2-ply expectimax scored by an externally-trained LightGBM model ($modelPath)",
          difficulty = opponentInfo.difficulty,
          isExperimental = true
        ),
        algorithm
      )

      val rescoreNote = rootRescore.fold("")(r => s", rootRescore=${r.modelPath} (weight=${r.weight})")
      val preRankNote = if preRankWithModel then ", preRank=model" else ""
      val bookNote    = book.fold("")(entries => s", book=${bookPath.getOrElse("")} (${entries.size} entries)")
      println(
        s"Loaded ONNX model from $modelPath (candidateLimit=$candidateLimit, features=$featureSet$rescoreNote$preRankNote$bookNote)"
      )
      // Opponent as baseline, expectimax bot as the measured side: the table row is the model bot's stats.
      BotMatchRunner.runArena(opponentInfo.id, Some(botId), games, StartFen)
    finally bot.close()
