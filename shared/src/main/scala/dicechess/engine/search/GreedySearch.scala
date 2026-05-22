package dicechess.engine.search

import dicechess.engine.domain.*

object GreedySearch extends SearchAlgorithm:

  override def findBestMove(state: GameState, dice: List[Int]): Option[ScoredSequence] =
    val paths = TurnGenerator.generateAllLegalTurnPaths(state, dice)
    if paths.isEmpty then None
    else
      val scoredPaths = paths.map { path =>
        // Evaluate the material balance at the end of the sequence.
        // We evaluate from the perspective of the player whose turn it currently is.
        val finalState = path.foldLeft(state)((s, m) => s.makeMove(m).copy(activeColor = s.activeColor))
        val score      = Evaluator.evaluateMaterial(finalState, state.activeColor)
        ScoredSequence(path, score)
      }
      Some(scoredPaths.maxBy(_.score))
