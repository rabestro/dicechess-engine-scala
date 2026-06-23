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
  *   1. `presets` — comma-separated controls as `base[+inc]` in seconds, e.g. `1+0,3+2,10+10` (default `1+0,3+2,10+10`)
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

  /** Parses a comma-separated preset spec (`"base[+inc]"` in seconds) into [[TimeControl]]s. */
  private[bench] def parsePresets(spec: String): List[TimeControl] =
    spec.split(',').toList.map(_.trim).filter(_.nonEmpty).map { token =>
      token.split('+').map(_.trim) match
        case Array(base, inc) =>
          TimeControl.ofSeconds(parseSec(token, base), parseSec(token, inc))
        case Array(base) =>
          TimeControl.ofSeconds(parseSec(token, base), 0)
        case _ => sys.error(s"Invalid time-control preset '$token' (expected 'base' or 'base+inc' in seconds)")
    }

  private def parseSec(token: String, value: String): Int =
    value.toIntOption.getOrElse(sys.error(s"Invalid time-control preset '$token': '$value' is not an integer"))
