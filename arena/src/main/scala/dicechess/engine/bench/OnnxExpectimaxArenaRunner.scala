package dicechess.engine.bench

import dicechess.engine.search.{
  BotInfo,
  BotRegistry,
  ExpectimaxConfig,
  KcpFeatures,
  OnnxExpectimaxSearch,
  OpeningBookBot,
  OpeningBookParser,
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
  * Usage:
  * `sbt 'arena/runMain dicechess.engine.bench.OnnxExpectimaxArenaRunner <model.onnx> --opponent aggressive --games 200 --features material'`
  */
import com.monovore.decline.*
import cats.implicits.*

object OnnxExpectimaxArenaRunner:
  def main(args: Array[String]): Unit =
    val command = Command(
      name = "OnnxExpectimaxArenaRunner",
      header = "Dice Chess Bot Arena - ONNX Expectimax Runner"
    ) {
      import ArenaOptions.*
      (
        modelPathOpt,
        opponentOpt("aggressive"),
        gamesOpt(200),
        candidateLimitOpt(),
        featuresOpt("material"),
        rescoreModelPathOpt,
        rescoreWeightOpt,
        preRankWithModelOpt,
        bookPathOpt,
        seedOpt(),
        jsonPathOpt
      ).mapN {
        (
            modelPath,
            opponentId,
            games,
            candidateLimit,
            featureSet,
            rescoreModelPath,
            rescoreWeight,
            preRankWithModel,
            bookPath,
            seed,
            jsonPath
        ) =>
          val extractFeatures = ArenaOptions.extractFeatures(featureSet)

          val rootRescore =
            rescoreModelPath.map(path => RootRescoreModel(path, KcpFeatures.extract, rescoreWeight))

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
            BotMatchRunner.runArena(
              opponentInfo.id,
              Some(botId),
              games,
              BotMatchRunner.StartFen,
              seed = seed,
              jsonPath = jsonPath
            )
          finally bot.close()
      }
    }
    command.parse(args.toIndexedSeq, sys.env) match
      case Left(help) =>
        System.err.println(help)
        sys.exit(1)
      case Right(_) => ()
