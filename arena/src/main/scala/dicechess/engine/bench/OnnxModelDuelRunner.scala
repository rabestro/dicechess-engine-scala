package dicechess.engine.bench

import scala.util.Using

import com.monovore.decline.*
import cats.implicits.*

import dicechess.engine.search.ExpectimaxConfig

/** Time-controlled arena for **two ONNX models against each other**.
  *
  * [[OnnxTimedArenaRunner]] puts one model on the clock against a *registry* bot, which is the right shape for asking
  * "is this model stronger than the hand-written baseline". It cannot answer the other question a training programme
  * runs into — "is model B stronger than model A" — because a registry bot is the only thing it accepts on the opposing
  * side. Serving the second model over the webhook protocol to reach [[TimedArenaRunner]] would work, but it buys
  * nothing except an HTTP hop and a container image: [[OnnxArenaBot]] already turns a model into a registry id. This
  * runner registers *both* sides and hands their ids to the same [[BotMatchRunner.runTimedMatch]] every other timed
  * measurement uses, so the measurement logic stays shared and only the wiring lives here.
  *
  * Both sides get the same [[ExpectimaxConfig]] (`--candidate-limit`), which is the point: holding search width equal
  * isolates the *evaluation* difference. Give the two sides different widths and the result stops being a statement
  * about the models.
  *
  * ⚠️ Timed results are **not** machine-independent — a slower box means fewer candidates per move inside the same
  * budget. Compare the two models only against each other, on one box, in one session; never against a number measured
  * elsewhere. This is the single easiest way to misread this harness.
  *
  * Usage:
  * `sbt 'arena/runMain dicechess.engine.bench.OnnxModelDuelRunner challenger.onnx defender.onnx --features rawboard --baseline-features rich --games 100 --candidate-limit 24 --presets 3+2 --seed 0 --json out.json'`
  */
object OnnxModelDuelRunner:

  /** Ids the two sides are registered under. Fixed rather than derived from the file names because [[BotRegistry]] is a
    * process-wide singleton keyed by id: a stable pair means a second run in the same JVM replaces the previous
    * registration instead of leaving stale bots behind.
    */
  private[bench] val ChallengerId = "onnx-challenger"
  private[bench] val DefenderId   = "onnx-defender"

  /** Registry presentation metadata, not a search parameter. Both sides share one value so nothing downstream can
    * mistake either for the harder bot — unlike [[OnnxTimedArenaRunner]], there is no baseline bot to inherit from.
    */
  private val ArenaDifficulty = 5

  private val challengerModelOpt: Opts[String] =
    Opts.argument[String](metavar = "challenger.onnx")

  private val defenderModelOpt: Opts[String] =
    Opts.argument[String](metavar = "defender.onnx")

  private val defenderFeaturesOpt: Opts[String] =
    Opts
      .option[String](
        "baseline-features",
        help = s"Feature set for the defender model: ${ArenaOptions.FeatureSets.mkString(", ")} (default: rich)"
      )
      .withDefault("rich")
      .validate("Unknown feature set")(set => ArenaOptions.FeatureSets.contains(set.toLowerCase))

  def main(args: Array[String]): Unit =
    ArenaOptions.runCommand(command, args)

  private[bench] val command: Command[Unit] = Command(
    name = "OnnxModelDuelRunner",
    header = "Dice Chess Bot Arena - ONNX model vs ONNX model, on a clock"
  ) {
    import ArenaOptions.*
    (
      challengerModelOpt,
      defenderModelOpt,
      featuresOpt("rich"),
      defenderFeaturesOpt,
      gamesOpt(10),
      candidateLimitOpt(),
      // One control by default, unlike the other timed runners: a duel of two ONNX models costs roughly twice a
      // model-vs-baseline run, and the question this runner exists for is normally posed at a single control.
      presetsOpt("3+2"),
      seedOpt(),
      jsonPathOpt,
      sprtConfigOpt
    ).mapN(runDuel)
  }

  private def runDuel(
      challengerModel: String,
      defenderModel: String,
      challengerFeatures: String,
      defenderFeatures: String,
      games: Int,
      candidateLimit: Int,
      presets: String,
      seed: Long,
      jsonPath: Option[String],
      sprtConfig: Option[SprtConfig]
  ): Unit =
    // Parsed before either model is loaded: a bad preset should cost nothing, and loading two onnxruntime sessions
    // only to reject the argument that follows them is a slow way to report a typo.
    val controls = TimedArenaRunner.parsePresets(presets)
    val config   = ExpectimaxConfig(candidateLimit)

    println(
      s"Timed model duel: $challengerModel (features=$challengerFeatures) vs " +
        s"$defenderModel (features=$defenderFeatures), K=$candidateLimit, controls=$presets, seed=$seed"
    )

    Using.resource(register(ChallengerId, challengerModel, challengerFeatures, config)) { _ =>
      Using.resource(register(DefenderId, defenderModel, defenderFeatures, config)) { _ =>
        val results = controls.map(tc =>
          BotMatchRunner.runTimedMatch(
            ChallengerId,
            DefenderId,
            TimedMatchSetup(games, tc, seed = seed, sprtConfig = sprtConfig)
          )
        )
        BotMatchRunner.printTimedSummary(ChallengerId, DefenderId, results)
        jsonPath.foreach { path =>
          BotMatchRunner.writeJsonReport(
            path,
            BotMatchRunner.timedReportJson(ChallengerId, DefenderId, games, seed, results)
          )
        }
      }
    }

  private def register(id: String, modelPath: String, featureSet: String, config: ExpectimaxConfig) =
    OnnxArenaBot.register(id, modelPath, featureSet, config, ArenaDifficulty, s"clock-aware model duel over $modelPath")
