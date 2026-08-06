package dicechess.engine.bench

import com.monovore.decline.*
import cats.implicits.*

import dicechess.engine.domain.{Color, GameState}
import dicechess.engine.search.{BotInfo, BotRegistry, ExpectimaxConfig, OnnxExpectimaxSearch}

/** Time-controlled arena for the 2-ply ONNX expectimax bot — the clock-aware counterpart of
  * [[OnnxExpectimaxArenaRunner]], filling the one cell [[TimedArenaRunner]] cannot reach on its own: it resolves both
  * sides through [[dicechess.engine.search.BotRegistry]] by id, and an ONNX model is not a registry bot until
  * registered as one. This wrapper closes exactly that gap — register the model as a custom bot, then hand its id to
  * the engine's own timed-match code, so the measurement logic stays [[BotMatchRunner]]'s and only the wiring lives
  * here.
  *
  * Every strength number this project publishes elsewhere is untimed, where a search runs to completion no matter how
  * long it takes. Production plays a real clock (e.g. `Fischer(300, 3)`), under which the *cost* of a position becomes
  * a strength term of its own — a model twice as expensive searches half as wide inside the same budget, a blind spot
  * the untimed arena cannot see. This is a live suspect whenever the seeded untimed arena and a live ladder disagree
  * about which of two models is stronger.
  *
  * ⚠️ Unlike the seeded untimed arena, timed results are **not** machine-independent: a slower box means fewer
  * candidates per move at the same wall-clock budget. Only compare models to each other on **one box, in one session**
  * — never against a number measured elsewhere. This is the single easiest way to misread this harness.
  *
  * Usage:
  * `sbt 'arena/runMain dicechess.engine.bench.OnnxTimedArenaRunner <modelPath> --features material --baseline aggressive --games 10 --candidate-limit 24 --presets 1+0,3+2,10+10 --seed 42'`
  */
object OnnxTimedArenaRunner:
  def main(args: Array[String]): Unit =
    val command = Command(
      name = "OnnxTimedArenaRunner",
      header = "Dice Chess Bot Arena - ONNX Timed Arena Runner"
    ) {
      import ArenaOptions.*
      (
        modelPathOpt,
        featuresOpt("material"),
        baselineOpt("aggressive"),
        gamesOpt(10),
        candidateLimitOpt(),
        presetsOpt("1+0,3+2,10+10"),
        seedOpt()
      ).mapN { (modelPath, featureSet, baseline, games, candidateLimit, presets, seed) =>
        val extractFeatures: (GameState, Color) => Array[Float] = ArenaOptions.extractFeatures(featureSet)

        val baselineInfo = BotRegistry.availableBots
          .find(_.id.equalsIgnoreCase(baseline))
          .getOrElse(sys.error(s"Baseline bot with ID '$baseline' not found in BotRegistry!"))

        val botId = "onnx-timed"
        val bot   = new OnnxExpectimaxSearch(modelPath, ExpectimaxConfig(candidateLimit), extractFeatures)
        try
          BotRegistry.registerCustomBot(
            BotInfo(
              id = botId,
              name = s"ONNX Expectimax ($featureSet, K=$candidateLimit)",
              description = s"clock-aware timed arena over $modelPath",
              difficulty = baselineInfo.difficulty,
              isExperimental = true
            ),
            bot
          )
          println(
            s"Timed arena: $modelPath (features=$featureSet, K=$candidateLimit) vs $baseline, controls=$presets, seed=$seed"
          )
          val controls = TimedArenaRunner.parsePresets(presets)
          val results  =
            controls.map(tc => BotMatchRunner.runTimedMatch(botId, baseline, TimedMatchSetup(games, tc, seed = seed)))
          BotMatchRunner.printTimedSummary(botId, baseline, results)
        finally bot.close()
      }
    }
    ArenaOptions.runCommand(command, args)
