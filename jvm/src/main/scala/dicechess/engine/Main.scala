package dicechess.engine

import dicechess.engine.cli.Commands
import org.jline.reader.{LineReaderBuilder, UserInterruptException, EndOfFileException}
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.TerminalBuilder

object Main:
  def main(args: Array[String]): Unit =
    val terminal = TerminalBuilder
      .builder()
      .system(true)
      .build()

    val completer = new StringsCompleter("eval", "arena", "help", "exit")

    val lineReader = LineReaderBuilder
      .builder()
      .terminal(terminal)
      .completer(completer)
      .build()

    println("==================================================")
    println("🎲♟️ Dice Chess Engine Interactive CLI")
    println("Type 'help' for commands, or 'exit' to quit.")
    println("==================================================")

    var running = true
    val parser  = new org.jline.reader.impl.DefaultParser()

    while running do
      try
        val line = lineReader.readLine("dicechess> ").trim
        if line.nonEmpty then
          if line == "exit" || line == "quit" then running = false
          else
            import scala.jdk.CollectionConverters.*
            val parsedLine = parser.parse(line, 0)
            val tokens     = parsedLine.words().asScala.toList

            Commands.rootCommand.parse(tokens) match
              case Left(help) => System.err.println(help)
              case Right(cmd) => Commands.execute(cmd)
      catch
        case _: UserInterruptException =>
        // Ignore, just clear the line
        case _: EndOfFileException =>
          running = false
        case e: Exception =>
          System.err.println(s"Error: ${e.getMessage}")
