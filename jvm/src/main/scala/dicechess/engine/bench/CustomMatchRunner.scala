package dicechess.engine.bench

object CustomMatchRunner {
  def main(args: Array[String]): Unit =
    BotMatchRunner.runArena(
      baseBotId = "mcts",
      opponentBotId = Some("monte-carlo"),
      gamesPerColor = 25,
      startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    )
}
