package dicechess.engine.search

import dicechess.engine.domain.*
import scala.util.Random

object GreedySearch extends SearchAlgorithm:

  override def findBestMove(state: GameState, dice: List[Int]): Option[ScoredSequence] =
    findBestMove(state, dice, new Random())

  def findBestMove(state: GameState, dice: List[Int], rand: Random): Option[ScoredSequence] =
    val paths = TurnGenerator.generateAllLegalTurnPaths(state, dice)
    if paths.isEmpty then None
    else
      val scoredPaths  = paths.map(path => SearchScoring.scorePath(state, path))
      val maxCriterion = scoredPaths.map(scored => (scored.score, terminalWinPreference(scored))).max
      val optimalPaths = scoredPaths.filter(scored => (scored.score, terminalWinPreference(scored)) == maxCriterion)
      Some(optimalPaths(rand.nextInt(optimalPaths.length)))

  private def terminalWinPreference(scored: ScoredSequence): Int =
    if scored.score == SearchScoring.TerminalWinScore then -scored.moves.size else 0
