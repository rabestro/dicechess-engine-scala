package dicechess.engine.search

import dicechess.engine.domain.*
import scala.util.Random

object RandomSearch extends SearchAlgorithm:

  private val rand = new Random()

  override def findBestMove(state: GameState, dice: List[Int]): Option[ScoredSequence] =
    val paths = TurnGenerator.generateAllLegalTurnPaths(state, dice)
    if paths.isEmpty then None
    else
      val randomPath = paths(rand.nextInt(paths.length))
      val finalState = randomPath.foldLeft(state)((s, m) => s.makeMove(m).copy(activeColor = s.activeColor))
      val score      = Evaluator.evaluateMaterial(finalState, state.activeColor)
      Some(ScoredSequence(randomPath, score))
