package dicechess.engine.bench

import dicechess.engine.domain.*
import dicechess.engine.search.*

/** Duels one model against itself at two different `candidateLimit` values, under a clock.
  *
  * The gap this fills: every other timed runner resolves its opponent through [[BotRegistry]] by id, so it can only
  * play an ONNX bot against a *built-in* bot. The question that matters for the pre-rank seam (`dicechess-ev#14`) is
  * narrower than that — hold the model fixed and vary only how many root candidates the search is allowed to expand.
  * Nothing could ask it in-process before this.
  *
  * Why it is worth asking. A probe over 300 corpus positions found that the material cut at `K=24` changes the search's
  * decision in 64% of positions with more than 24 legal turns, and that the hidden turns are usually better by
  * loss-taint margins — the narrow search's best candidate loses the king on some rolls where the wide one does not.
  * Yet the untimed K-curve flattened past 24. Both can only be true if the value never converts to wins, or if the
  * K-curve was measured too coarsely to see it (n=100 per cell resolves about ±10pp). This runner settles it by
  * changing one variable and nothing else — and it needs no trained ranker, so a null here closes `dicechess-ev#14`
  * before any training data is generated.
  *
  * Timed on purpose, unlike the probe. Width costs time: a wider search that finds a better move but arrives late is
  * not an improvement. That trade-off only exists under a clock, and it is hardware-dependent — the same comparison has
  * measured +28 elo on fast cores against +82 on an Ampere A1 — so the machine is part of the experiment, not a detail
  * of it.
  *
  * Reports through [[BotMatchRunner.runTimedMatch]], so a `--json` run carries the mirrored-pair histogram and the
  * `resolution` block (#508): what difference the run could actually have resolved, rather than only a win rate.
  *
  * Usage: `runMain dicechess.engine.bench.OnnxWidthDuelRunner <model.onnx> [wideK] [narrowK] [features] [gamesPerColor]
  * [presets] [seed] [--sprt elo0,elo1,alpha,beta] [--json report.json]`
  */
object OnnxWidthDuelRunner:

  /** Everything the duel needs, parsed. Separated from [[main]] so the parsing — defaults, the two rejects, the
    * feature-set mapping — is testable without an ONNX session or a game being played.
    */
  final private[bench] case class DuelArgs(
      modelPath: String,
      wideK: Int,
      narrowK: Int,
      featureSet: String,
      gamesPerColor: Int,
      presets: String,
      seed: Long,
      sprtConfig: Option[SprtConfig],
      jsonPath: Option[String]
  ):
    /** The leaf extractor named by `featureSet`; fails fast rather than serving a model the wrong input width. */
    def extractFeatures: (GameState, Color) => Array[Float] = featureSet.toLowerCase match
      case "material" => OnnxFeatures.extract
      case "rich"     => RichFeatures.extract
      case "kcp"      => KcpFeatures.extract
      case "rawboard" => RawBoardFeatures.extract
      case other      => sys.error(s"Unknown feature set '$other' (expected 'material', 'rich', 'kcp', or 'rawboard')")

  /** A positional integer that must be positive, defaulting only when the argument is ABSENT.
    *
    * `toIntOption.getOrElse(default)` would silently swallow a typo: `wideK=abc` would run at 48 and the report would
    * look perfectly normal. For a measurement tool that is worse than crashing — the number would be quoted later as
    * though it answered the question that was asked.
    */
  private def positiveInt(positional: Array[String], index: Int, name: String, default: Int): Int =
    positional.lift(index) match
      case None      => default
      case Some(raw) =>
        val parsed = raw.toIntOption.getOrElse(sys.error(s"$name must be an integer, got '$raw'"))
        if parsed <= 0 then sys.error(s"$name must be positive, got $parsed")
        parsed

  /** Same contract for the seed. A silently defaulted seed is the quietest failure of the three: two runs meant to be
    * independent samples would replay the identical dice stream, and nothing in the output would say so.
    */
  private def longArg(positional: Array[String], index: Int, name: String, default: Long): Long =
    positional.lift(index) match
      case None      => default
      case Some(raw) => raw.toLongOption.getOrElse(sys.error(s"$name must be an integer, got '$raw'"))

  private[bench] def parseArgs(args: Array[String]): DuelArgs =
    val (afterJson, jsonPath)    = BotMatchRunner.extractJsonPath(args)
    val (positional, sprtConfig) = TimedArenaRunner.extractSprtConfig(afterJson)

    val modelPath = positional.headOption.getOrElse(
      sys.error(
        "Usage: OnnxWidthDuelRunner <model.onnx> [wideK] [narrowK] [features] [gamesPerColor] [presets] [seed] " +
          "[--sprt elo0,elo1,alpha,beta] [--json report.json]"
      )
    )
    val wideK   = positiveInt(positional, 1, "wideK", 48)
    val narrowK = positiveInt(positional, 2, "narrowK", 24)
    val games   = positiveInt(positional, 4, "gamesPerColor", 10)
    val seed    = longArg(positional, 6, "seed", 42L)

    // Strictly greater, not merely different. The wide side is reported as the bot under test, so a
    // score above 50% is read as "widening helped" — passing the narrower limit as `wideK` would make
    // that sentence false while every label stayed self-consistent. Swap the arguments instead.
    if wideK <= narrowK then sys.error(s"wideK ($wideK) must be greater than narrowK ($narrowK)")

    DuelArgs(
      modelPath = modelPath,
      wideK = wideK,
      narrowK = narrowK,
      featureSet = positional.lift(3).getOrElse("rich"),
      gamesPerColor = games,
      presets = positional.lift(5).getOrElse("3+2"),
      seed = seed,
      sprtConfig = sprtConfig,
      jsonPath = jsonPath
    )

  def main(args: Array[String]): Unit =
    val parsed          = parseArgs(args)
    val extractFeatures = parsed.extractFeatures
    import parsed.{featureSet, gamesPerColor as games, jsonPath, modelPath, narrowK, presets, seed, sprtConfig, wideK}

    // Two sessions over the SAME file rather than one shared session: each side owns its own ONNX session exactly as a
    // deployed bot does, so neither gains from a warmed cache the other filled.
    val wide   = new OnnxExpectimaxSearch(modelPath, ExpectimaxConfig(wideK), extractFeatures)
    val narrow = new OnnxExpectimaxSearch(modelPath, ExpectimaxConfig(narrowK), extractFeatures)
    try
      println(
        s"Width duel: $modelPath ($featureSet) K=$wideK vs K=$narrowK, " +
          s"$games mirrored pairs per control, controls=$presets, seed=$seed" +
          sprtConfig.fold("")(_ => " (SPRT stopping on)")
      )
      // The WIDE side is the bot under test, so a reported score above 50% means widening helped.
      val results = TimedArenaRunner
        .parsePresets(presets)
        .map(tc =>
          BotMatchRunner.runTimedMatch(wide, narrow, TimedMatchSetup(games, tc, seed = seed, sprtConfig = sprtConfig))
        )

      val wideId   = s"K=$wideK"
      val narrowId = s"K=$narrowK"
      BotMatchRunner.printTimedSummary(wideId, narrowId, results)
      jsonPath.foreach { path =>
        BotMatchRunner.writeJsonReport(path, BotMatchRunner.timedReportJson(wideId, narrowId, games, seed, results))
        println(s"Wrote $path")
      }
    finally
      wide.close()
      narrow.close()
