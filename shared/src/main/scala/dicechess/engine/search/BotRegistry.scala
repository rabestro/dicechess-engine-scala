package dicechess.engine.search

/** Metadata representing a Search Algorithm (Bot) in the engine.
  *
  * @param id
  *   Unique identifier (e.g., "random", "greedy")
  * @param name
  *   Human-readable name
  * @param description
  *   Brief description of the bot's behavior
  * @param difficulty
  *   Difficulty level from 1 to 10
  * @param isExperimental
  *   True if the bot is in beta or experimental phase
  */
case class BotInfo(
    id: String,
    name: String,
    description: String,
    difficulty: Int,
    isExperimental: Boolean
)

/** Central registry for all available search algorithms in the engine.
  */
object BotRegistry:

  private val bots: Map[String, (BotInfo, SearchAlgorithm)] = Map(
    "random" -> (
      BotInfo(
        id = "random",
        name = "Random",
        description = "Makes a random valid move.",
        difficulty = 1,
        isExperimental = false
      ),
      RandomSearch
    ),
    "checkmate-aware" -> (
      BotInfo(
        id = "checkmate-aware",
        name = "Checkmate Aware",
        description = "Prioritizes immediate checkmate and king safety, but remains material-blind.",
        difficulty = 2,
        isExperimental = false
      ),
      CheckmateAwareSearch
    ),
    "greedy" -> (
      BotInfo(
        id = "greedy",
        name = "Greedy",
        description = "Always tries to capture the highest value piece without considering consequences.",
        difficulty = 3,
        isExperimental = false
      ),
      GreedySearch
    ),
    "greedy-v2" -> (
      BotInfo(
        id = "greedy-v2",
        name = "Cautious Greedy",
        description =
          "Tries to capture the highest value piece, but avoids leaving the King exposed to immediate capture.",
        difficulty = 4,
        isExperimental = false
      ),
      GreedySearchV2
    ),
    "aggressive" -> (
      BotInfo(
        id = "aggressive",
        name = "Aggressive",
        description = "Actively hunts your pieces and targets your king, pushing pawns forward aggressively.",
        difficulty = 5,
        isExperimental = false
      ),
      AggressiveSearch
    ),
    "prudent" -> (
      BotInfo(
        id = "prudent",
        name = "Prudent",
        description =
          "Evaluates king and queen capture probability over all possible dice rolls; avoids positions where the opponent is likely to capture the king on the next turn.",
        difficulty = 6,
        isExperimental = true
      ),
      PrudentSearch
    ),
    "monte-carlo" -> (
      BotInfo(
        id = "monte-carlo",
        name = "Monte-Carlo",
        description =
          "Estimates the full-game win probability of each candidate turn with Rao-Blackwellized Monte-Carlo rollouts and plays the highest.",
        difficulty = 7,
        isExperimental = true
      ),
      MonteCarloSearch
    )
  )

  /** Returns all available bots sorted by difficulty. */
  val availableBots: List[BotInfo] = bots.values.map(_._1).toList.sortBy(_.difficulty)

  /** Looks up a search algorithm by its bot ID.
    *
    * @param id
    *   The bot ID to lookup (case-insensitive)
    * @return
    *   The algorithm if found, or None.
    */
  def getAlgorithm(id: String): Option[SearchAlgorithm] =
    Option(id).flatMap(i => bots.get(i.toLowerCase)).map(_._2)

  /** Returns the default algorithm (Greedy). */
  def defaultAlgorithm: SearchAlgorithm = GreedySearch
