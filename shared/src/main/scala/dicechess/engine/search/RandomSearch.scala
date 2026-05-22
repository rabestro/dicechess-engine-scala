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
      Some(SearchScoring.scorePath(state, randomPath))
