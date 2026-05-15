package dicechess.engine.movegen

import dicechess.engine.domain.*

object Perft:

  /** Counts the number of leaf nodes at a given depth for a fixed dice roll sequence. In this simplified version for
    * testing move generation, we assume the same dice roll applies to all micro-moves in the sequence.
    */
  def countNodes(state: GameState, diceRoll: Int, depth: Int): Long =
    if depth == 0 then return 1

    val moves = MoveGenerator.generateMoves(state, diceRoll)
    if depth == 1 then return moves.length.toLong

    var nodes = 0L
    for mv <- moves do
      val nextState = state.makeMove(mv)
      nodes += countNodes(nextState, diceRoll, depth - 1)

    nodes

  /** Performs a divide Perft: lists each move and the number of nodes it produces. Useful for debugging discrepancies.
    */
  def divide(state: GameState, diceRoll: Int, depth: Int): Map[String, Long] =
    val moves = MoveGenerator.generateMoves(state, diceRoll)
    moves.map { mv =>
      val notation  = s"${mv.fromSquare}${mv.toSquare}" // Simple notation for now
      val nextState = state.makeMove(mv)
      val count     = if depth > 1 then countNodes(nextState, diceRoll, depth - 1) else 1L
      notation -> count
    }.toMap
