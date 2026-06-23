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
  *
  * Example: `sbt 'rootJVM/runMain dicechess.engine.bench.TimedArenaRunner monte-carlo aggressive 10 1+0,3+2,10+10'`
  */
object TimedArenaRunner:

  def main(args: Array[String]): Unit =
    val botId    = args.lift(0).getOrElse("monte-carlo")
    val baseline = args.lift(1).getOrElse("aggressive")
    val games    = args.lift(2).flatMap(_.toIntOption).getOrElse(10)
    val presets  = args.lift(3).getOrElse("1+0,3+2,10+10")

    if games <= 0 then sys.error(s"gamesPerColor must be > 0, got $games")

    try
      val controls = parsePresets(presets)
      val results  = controls.map(tc => BotMatchRunner.runTimedMatch(botId, baseline, games, tc))
      BotMatchRunner.printTimedSummary(botId, baseline, results)
    catch
      case e: Exception =>
        System.err.println(e.getMessage)
        sys.exit(1)

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
