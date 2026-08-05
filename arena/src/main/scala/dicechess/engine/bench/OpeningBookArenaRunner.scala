package dicechess.engine.bench

import scala.io.Source

import dicechess.engine.search.{BotInfo, BotRegistry, OpeningBookBot, OpeningBookParser}

/** Local arena between a base bot and the same bot decorated with an opening book.
  *
  * The book is read from a file path at runtime, so the book data never has to be committed to this public repository —
  * keep your `opening_book.json` git-ignored. The runner registers `<base>-book` (the base bot wrapped by
  * [[dicechess.engine.search.OpeningBookBot]]) and pits the base bot against it via [[BotMatchRunner.runArena]].
  *
  * Usage: `runMain dicechess.engine.bench.OpeningBookArenaRunner <baseBotId> <bookPath> [gamesPerColor]` (or
  * `mise run arena:book <baseBotId> <bookPath> [games]`).
  */
object OpeningBookArenaRunner:

  private val StartFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  def main(args: Array[String]): Unit =
    val baseBotId = args.headOption.getOrElse("aggressive")
    val bookPath  = args
      .lift(1)
      .getOrElse(sys.error("Usage: OpeningBookArenaRunner <baseBotId> <bookPath> [gamesPerColor]"))
    val games = args.lift(2).flatMap(_.toIntOption).getOrElse(50)

    val baseInfo = BotRegistry.availableBots
      .find(_.id.equalsIgnoreCase(baseBotId))
      .getOrElse(sys.error(s"Unknown base bot '$baseBotId'"))
    val baseAlgorithm = BotRegistry.getAlgorithm(baseBotId).get

    val json =
      val source = Source.fromFile(bookPath)
      try source.mkString
      finally source.close()
    val book = OpeningBookParser
      .parse(json)
      .fold(error => sys.error(s"Failed to parse opening book '$bookPath': ${error.getMessage}"), identity)

    val bookId = s"${baseInfo.id}-book"
    BotRegistry.registerCustomBot(
      BotInfo(
        id = bookId,
        name = s"${baseInfo.name} + Book",
        description = s"${baseInfo.id} decorated with an opening book ($bookPath)",
        difficulty = baseInfo.difficulty,
        isExperimental = true
      ),
      OpeningBookBot.decorate(baseAlgorithm, book)
    )

    println(s"Loaded ${book.size} opening-book entries from $bookPath")
    BotMatchRunner.runArena(baseBotId, Some(bookId), games, StartFen)
