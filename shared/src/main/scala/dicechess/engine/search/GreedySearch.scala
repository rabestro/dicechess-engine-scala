package dicechess.engine.search

import dicechess.engine.domain.*

object GreedySearch extends SearchAlgorithm:

  override def findBestMove(state: GameState, dice: List[Int]): Option[ScoredSequence] =
    val paths = TurnGenerator.generateAllLegalTurnPaths(state, dice)
    if paths.isEmpty then None
    else
      val scoredPaths = paths.map(path => SearchScoring.scorePath(state, path))
      Some(scoredPaths.maxBy(scored => (scored.score, terminalWinPreference(scored))))

  private def terminalWinPreference(scored: ScoredSequence): Int =
    if scored.score == SearchScoring.TerminalWinScore then -scored.moves.size else 0
