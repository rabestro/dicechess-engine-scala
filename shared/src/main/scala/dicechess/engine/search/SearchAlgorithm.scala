package dicechess.engine.search

import dicechess.engine.domain.{GameState, Move}

/** Represents a sequence of moves along with the evaluated score of the final position. */
case class ScoredSequence(moves: List[Move], score: Int)

trait SearchAlgorithm:
  /** Finds the best sequence of moves for the given state and available dice.
    *
    * @param state
    *   the current GameState
    * @param dice
    *   the available dice rolls (1-6)
    * @return
    *   Some(ScoredSequence) if a move is possible, or None if the player must pass.
    */
  def findBestMove(state: GameState, dice: List[Int]): Option[ScoredSequence]
