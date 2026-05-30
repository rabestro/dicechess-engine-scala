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
    "greedy" -> (
      BotInfo(
        id = "greedy",
        name = "Greedy",
        description = "Always tries to capture the highest value piece without considering consequences.",
        difficulty = 3,
        isExperimental = false
      ),
      GreedySearch
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
    if id == null then None else bots.get(id.toLowerCase).map(_._2)

  /** Returns the default algorithm (Greedy). */
  def defaultAlgorithm: SearchAlgorithm = GreedySearch
