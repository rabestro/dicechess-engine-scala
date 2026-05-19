package dicechess.engine.movegen

import dicechess.engine.domain.*

/** Legal moves filter for Dice Chess.
  *
  * This object implements the filtering logic required to enforce the Maximum Micro-moves Rule, which dictates that
  * players must choose moves that maximize the number of micro-moves they can play in a single turn.
  */
object LegalMovesFilter:

  /** Filters and returns the legal moves for a given position and rolled dice.
    *
    * Currently implements a naive placeholder that returns all pseudo-legal moves for the rolled dice, matching the
    * initial JavaScript API behavior prior to full recursive path validation.
    *
    * @param state
    *   The current game state.
    * @param dice
    *   The list of available dice rolls (1-6).
    * @return
    *   A list of legal moves under the Maximum Micro-moves Rule.
    */
  def filterMaximalMoves(state: GameState, dice: List[Int]): List[Move] =
    dice.flatMap(d => MoveGenerator.generateMoves(state, d))
