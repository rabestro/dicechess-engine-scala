package dicechess.engine.movegen

import dicechess.engine.domain.*

/** Data structure representing a single Chess move generator test case.
  *
  * @param fen
  *   The position in Forsyth-Edwards Notation.
  * @param dice
  *   The list of available dice rolls (1-6).
  * @param expectedMoves
  *   The expected legal moves in standard algebraic notation (e.g., "e2e4", "e7e8q").
  * @param description
  *   An optional description of the test scenario.
  */
case class MoveGenTestCase(
    fen: String,
    dice: List[Int],
    expectedMoves: List[String],
    description: Option[String] = None
)

/** Custom DSL and utilities for writing elegant and compact move generator tests.
  */
object ChessDsl:

  extension (move: Move)
    /** Converts a Move into its standard algebraic notation string (e.g., "e2e4" or "e7e8q").
      */
    def toNotation: String =
      val promStr = if move.isPromotion then
        move.flags match
          case Move.KnightPromotion | Move.KnightPromoCapture => "n"
          case Move.BishopPromotion | Move.BishopPromoCapture => "b"
          case Move.RookPromotion | Move.RookPromoCapture     => "r"
          case Move.QueenPromotion | Move.QueenPromoCapture   => "q"
          case _                                              => ""
      else ""
      s"${move.fromSquare.toNotation}${move.toSquare.toNotation}$promStr"

  extension (fen: String)
    // 1-die roll (Int or PieceType)
    def withDice(die: Int): FenWithDice =
      FenWithDice(fen, List(die))

    @scala.annotation.targetName("withDicePiece")
    def withDice(die: PieceType): FenWithDice =
      FenWithDice(fen, List(die.diceValue))

    // 2-dice roll
    def withDice(dice: (Int, Int)): FenWithDice =
      FenWithDice(fen, List(dice._1, dice._2))

    @scala.annotation.targetName("withDicePiece2")
    def withDice(dice: (PieceType, PieceType)): FenWithDice =
      FenWithDice(fen, List(dice._1.diceValue, dice._2.diceValue))

    // 3-dice roll
    def withDice(dice: (Int, Int, Int)): FenWithDice =
      FenWithDice(fen, List(dice._1, dice._2, dice._3))

    @scala.annotation.targetName("withDicePiece3")
    def withDice(dice: (PieceType, PieceType, PieceType)): FenWithDice =
      FenWithDice(fen, List(dice._1.diceValue, dice._2.diceValue, dice._3.diceValue))

    // General list fallback
    def withDice(diceList: List[Int]): FenWithDice =
      FenWithDice(fen, diceList)

  case class FenWithDice(fen: String, dice: List[Int]):
    /** Assigns a description to this test case.
      */
    def describedAs(desc: String): FenWithDiceAndDesc =
      FenWithDiceAndDesc(fen, dice, desc)

    /** Specifies the expected legal moves that the generator should produce.
      */
    def shouldYield(moves: String*): MoveGenTestCase =
      MoveGenTestCase(fen, dice, moves.toList, None)

  case class FenWithDiceAndDesc(fen: String, dice: List[Int], desc: String):
    /** Specifies the expected legal moves that the generator should produce.
      */
    def shouldYield(moves: String*): MoveGenTestCase =
      MoveGenTestCase(fen, dice, moves.toList, Some(desc))
