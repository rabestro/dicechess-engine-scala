package dicechess.engine.bench

/** Executable entry point for the time-controlled arena (the #372 gate).
  *
  * Plays a time-budgeted bot against a baseline across one or more controls and prints win-rate, flag-rate, and the
  * latency distribution per control. Unlike a hardcoded harness, every parameter is an argument.
  *
  * Arguments (all optional, positional):
  *   1. `botUnderTest` — id of the bot being evaluated (default `monte-carlo`)
  *   1. `baseline` — id of the opponent (default `aggressive`)
  *   1. `gamesPerColor` — games on each side, so total is `2 ×` this (default `10`)
  *   1. `presets` — comma-separated chess-clock controls in `minutes[+incrementSeconds]` notation, e.g. `1+0,3+2,10+10`
  *      (1-minute bullet; 3 min + 2 s; 10 min + 10 s) (default `1+0,3+2,10+10`)
  *   1. `seed` — dice seed (default `42`, which reproduces this runner's original behaviour). Distinct seeds draw
  *      independent samples of the same matchup — run K shards with distinct seeds spaced at least `gamesPerColor`
  *      apart (e.g. `0, 1000, 2000, ...`) on separate cores or machines, and sum the tallies for K times the games in
  *      the same wall-clock time.
  *
  * Either bot position also accepts an `http(s)://` URL: the opponent then lives behind the platform's webhook protocol
  * and is driven by [[WebhookBot]] (#526) — deliveries are HMAC-signed with the secret from the
  * `DICECHESS_WEBHOOK_SECRET` env var (required for webhook opponents; never passed on the command line), an ownership
  * handshake runs before the first game, and the endpoint gets its full remaining clock per turn.
  *
  * An optional `--json <path>` flag (anywhere in the arguments) additionally writes the machine-readable report from
  * [[BotMatchRunner.timedReportJson]] to `path` — the human-readable table is always printed regardless.
  *
  * An optional `--sprt <elo0>,<elo1>,<alpha>,<beta>` flag (e.g. `--sprt 0,20,0.05,0.05`) turns on SPRT stopping (#522):
  * `gamesPerColor` becomes a cap instead of a fixed count, and each control stops as soon as the mirrored-pair evidence
  * is decisive — see [[BotMatchRunner.runTimedMatch]].
  *
  * Example: `sbt 'arena/runMain dicechess.engine.bench.TimedArenaRunner monte-carlo aggressive 10 1+0,3+2,10+10'`
  */
object TimedArenaRunner:

  def main(args: Array[String]): Unit =
    val (afterJson, jsonPath)    = BotMatchRunner.extractJsonPath(args)
    val (positional, sprtConfig) = extractSprtConfig(afterJson)
    val botId                    = positional.lift(0).getOrElse("monte-carlo")
    val baseline                 = positional.lift(1).getOrElse("aggressive")
    val games                    = positional.lift(2).flatMap(_.toIntOption).getOrElse(10)
    val presets                  = positional.lift(3).getOrElse("1+0,3+2,10+10")
    val seed                     = positional.lift(4) match
      case None       => 42L
      case Some(spec) => spec.toLongOption.getOrElse(sys.error(s"Invalid seed '$spec': not a valid Long"))

    if games <= 0 then sys.error(s"gamesPerColor must be > 0, got $games")

    try
      val controls = parsePresets(presets)
      val results  =
        controls.map(tc =>
          BotMatchRunner.runTimedMatch(
            botId,
            baseline,
            TimedMatchSetup(games, tc, seed = seed, sprtConfig = sprtConfig)
          )
        )
      BotMatchRunner.printTimedSummary(botId, baseline, results)
      jsonPath.foreach { path =>
        BotMatchRunner.writeJsonReport(path, BotMatchRunner.timedReportJson(botId, baseline, games, seed, results))
      }
    catch
      case e: Exception =>
        System.err.println(e.getMessage)
        sys.exit(1)

  /** Extracts an optional `--sprt <elo0>,<elo1>,<alpha>,<beta>` flag from `args`, returning the remaining positional
    * arguments (with both tokens removed) and the config, if present.
    */
  private[bench] def extractSprtConfig(args: Array[String]): (Array[String], Option[SprtConfig]) =
    val idx = args.indexOf("--sprt")
    if idx < 0 then (args, None)
    else if idx + 1 >= args.length then sys.error("--sprt requires 'elo0,elo1,alpha,beta'")
    else
      val spec  = args(idx + 1)
      val parts = spec.split(',').map(_.trim)
      if parts.length != 4 then sys.error(s"Invalid --sprt spec '$spec': expected 'elo0,elo1,alpha,beta'")
      val elo0  = sprtNumber(spec, parts(0))
      val elo1  = sprtNumber(spec, parts(1))
      val alpha = sprtRate(spec, "alpha", sprtNumber(spec, parts(2)))
      val beta  = sprtRate(spec, "beta", sprtNumber(spec, parts(3)))
      if elo0 >= elo1 then sys.error(s"Invalid --sprt spec '$spec': elo0 must be < elo1")
      (args.patch(idx, Nil, 2), Some(SprtConfig(elo0, elo1, alpha, beta)))

  private def sprtNumber(spec: String, value: String): Double =
    value.toDoubleOption.getOrElse(sys.error(s"Invalid --sprt spec '$spec': '$value' is not a number"))

  /** Error rates must sit strictly inside `(0, 1)`: [[Sprt.test]]'s bounds are logarithms of `beta / (1 - alpha)` and
    * `(1 - beta) / alpha`, which degenerate to `±Infinity`/`NaN` at or beyond the interval's ends — silently making one
    * verdict unreachable, the first pair always decisive, or every LLR comparison `false` forever.
    */
  private def sprtRate(spec: String, name: String, value: Double): Double =
    if value <= 0.0 || value >= 1.0 then sys.error(s"Invalid --sprt spec '$spec': $name must be in (0, 1)")
    value

  /** Parses comma-separated chess-clock presets in `minutes[+incrementSeconds]` notation (e.g. `1+0`, `3+2`, `10+10`)
    * into [[TimeControl]]s. The base is a positive integer number of minutes; the increment a non-negative number of
    * seconds (default 0). Requires at least one preset.
    */
  private[bench] def parsePresets(spec: String): List[TimeControl] =
    val controls = spec.split(',').toList.map(_.trim).filter(_.nonEmpty).map { token =>
      token.split('+').map(_.trim) match
        case Array(base, inc) => TimeControl(baseMinutes(token, base) * 60_000L, incrementSeconds(token, inc) * 1000L)
        case Array(base)      => TimeControl(baseMinutes(token, base) * 60_000L, 0L)
        case _ => sys.error(s"Invalid time-control preset '$token' (expected 'minutes' or 'minutes+incrementSeconds')")
    }
    if controls.isEmpty then sys.error("At least one time-control preset is required")
    controls

  private def baseMinutes(token: String, value: String): Long =
    val minutes = wholeNumber(token, value)
    if minutes <= 0 then sys.error(s"Invalid time-control preset '$token': base minutes must be > 0")
    minutes

  private def incrementSeconds(token: String, value: String): Long =
    val seconds = wholeNumber(token, value)
    if seconds < 0 then sys.error(s"Invalid time-control preset '$token': increment seconds must be >= 0")
    seconds

  private def wholeNumber(token: String, value: String): Long =
    value.toLongOption.getOrElse(sys.error(s"Invalid time-control preset '$token': '$value' is not an integer"))
