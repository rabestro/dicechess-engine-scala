package dicechess.engine

import dicechess.engine.domain.*

object Main:
  private val Separator = "=" * 50

  def main(args: Array[String]): Unit =
    println(Separator)
    println("🎲♟️ Dice Chess Engine (Scala 3) successfully initialized!")
    println(Separator)

    // Demonstrate basic domain model creation
    val startSquare = Square('e', 2)
    val endSquare   = Square('e', 4)
    val pawnMove    = MicroMove(startSquare, endSquare)

    println(s"Created a micro-move representation: ${pawnMove.toNotation}")
    println("Ready for the Hackathon! 🚀")
    println(Separator)
