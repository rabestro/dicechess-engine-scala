package dicechess.engine.cli

import cats.implicits.*
import com.monovore.decline.*
import dicechess.engine.bench.BotMatchRunner
import dicechess.engine.domain.FenParser
import dicechess.engine.search.KingCaptureProbability

sealed trait CliCommand
case class EvalCommand(fen: String, unicode: Boolean) extends CliCommand
case class ArenaCommand(base: String, opponent: String, games: Int, fen: Option[String]) extends CliCommand

object Commands:

  val fenOpt = Opts.arguments[String]("FEN").map(_.toList.mkString(" "))
  
  val unicodeOpt = Opts.flag("unicode", help = "Use Unicode characters for chess pieces").orFalse

  val evalCommand = Opts.subcommand("eval", "Evaluate a position and print the board") {
    (fenOpt, unicodeOpt).mapN(EvalCommand.apply)
  }

  val baseOpt = Opts.argument[String](metavar = "BASE")
  val opponentOpt = Opts.argument[String](metavar = "OPPONENT")
  val gamesOpt = Opts.option[Int]("games", help = "Number of games per color").withDefault(50)
  val startFenOpt = Opts.option[String]("fen", help = "Starting FEN").orNone

  val arenaCommand = Opts.subcommand("arena", "Run a bot match") {
    (baseOpt, opponentOpt, gamesOpt, startFenOpt).mapN(ArenaCommand.apply)
  }

  val rootCommand = Command("dicechess", "Dice Chess Engine CLI") {
    evalCommand orElse arenaCommand
  }

  def execute(command: CliCommand): Unit = command match
    case EvalCommand(fen, unicode) =>
      FenParser.parse(fen) match
        case Some(state) =>
          println(BoardPrinter.printBoard(state, unicode))
          
          val opponent = state.activeColor.opponent
          val kcp = KingCaptureProbability.kingCaptureProbability(state, opponent)
          val qcp = KingCaptureProbability.queenCaptureProbability(state, opponent)
          
          println(f"King Capture Probability (for ${opponent}): ${kcp * 100}%.1f%%")
          println(f"Queen Capture Probability (for ${opponent}): ${qcp * 100}%.1f%%")
          
        case None =>
          println("Error: Invalid FEN string")

    case ArenaCommand(base, opponent, games, fen) =>
      try
        val actualFen = fen.getOrElse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        BotMatchRunner.runArena(base, Some(opponent), games, actualFen)
      catch
        case e: Exception => println(s"Error: ${e.getMessage}")
