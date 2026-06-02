package dicechess.engine

import dicechess.engine.domain.*
import dicechess.engine.search.*
import scala.io.StdIn

/** JVM entry point for the Dice Chess Engine (CLI Analyzer).
  */
object Main:
  private val Separator = "=" * 50

  def main(args: Array[String]): Unit =
    println(Separator)
    println("🎲♟️ Dice Chess CLI Analyzer & Arena")
    println(Separator)

    // Start with initial position
    val initialState = FenParser.parse(FenParser.InitialPosition).toOption.get
    runCliLoop(initialState)

  private def runCliLoop(state: GameState): Unit =
    renderBoard(state)
    renderAnalysis(state)

    print("\nEnter UCI move (e.g., e2e4), bot ID (e.g., prudent), or 'exit': ")
    val input = StdIn.readLine().trim.toLowerCase

    input match
      case "exit" | "quit" =>
        println("Exiting...")
      case botId if BotRegistry.getAlgorithm(botId).isDefined =>
        val algo = BotRegistry.getAlgorithm(botId).get
        algo.findBestMove(state) match
          case Some(scored) =>
            val bestMove = scored.moves.head
            val notation = bestMove.fromSquare.toNotation + bestMove.toSquare.toNotation
            println(s"Bot '$botId' suggests: $notation")
            runCliLoop(state.makeMove(bestMove))
          case None =>
            println("Bot found no moves.")
            runCliLoop(state)
      case uciMove =>
        // Try manual move
        // This is a bit simplified, ideally should check if it's a valid micro-move sequence
        // For CLI analyzer, just make the move
        Square.fromNotation(uciMove.take(2)) match
          case Some(from) =>
            Square.fromNotation(uciMove.drop(2).take(2)) match
              case Some(to) =>
                val move = MicroMove(from, to)
                runCliLoop(state.makeMove(move))
              case None =>
                println("Invalid move format.")
                runCliLoop(state)
          case None =>
            println("Unknown command.")
            runCliLoop(state)

  private def renderBoard(state: GameState): Unit =
    println("\n  a b c d e f g h")
    for (r <- 7 to 0 by -1) {
      print(s"${r + 1} ")
      for (f <- 0 until 8) {
        val sq = Square.fromIndex(r * 8 + f)
        val p  = state.mailbox(sq)
        if (p.isEmpty) print(". ")
        else {
          val char   = p.pieceType.asNotation
          val symbol = p.color match
            case Color.White => char.toUpperCase
            case Color.Black => char.toLowerCase
          print(s"$symbol ")
        }
      }
      println(s" ${r + 1}")
    }
    println("  a b c d e f g h")
    println(s"Active: ${if (state.activeColor.isWhite) "White" else "Black"}")
    println(s"Dice: ${state.dicePool}")

  private def renderAnalysis(state: GameState): Unit =
    val kingProb  = KingCaptureProbability.kingCaptureProbability(state, state.activeColor)
    val queenProb = KingCaptureProbability.queenCaptureProbability(state, state.activeColor)
    val score     = Evaluator.evaluateMaterial(state, state.activeColor)
    println(f"\n--- Analysis (Active: ${if (state.activeColor.isWhite) "White" else "Black"}) ---")
    println(f"King Capture Prob:  $kingProb%.2f")
    println(f"Queen Capture Prob: $queenProb%.2f")
    println(f"Material Score:     $score cp")
