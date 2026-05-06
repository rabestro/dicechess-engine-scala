package dicechess.engine

import dicechess.engine.domain.*

object Main:
  def main(args: Array[String]): Unit =
    println("==================================================")
    println("🎲♟️ Dice Chess Engine (Scala 3) successfully initialized!")
    println("==================================================")
    
    // Demonstrate basic domain model creation
    val startSquare = Square('e', 2)
    val endSquare = Square('e', 4)
    val pawnMove = MicroMove(startSquare, endSquare)
    
    println(s"Created a micro-move representation: ${pawnMove.toNotation}")
    println("Ready for the Hackathon! 🚀")
    println("==================================================")
