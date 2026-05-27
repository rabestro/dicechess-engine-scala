package dicechess.engine.search

import dicechess.engine.domain.*
import scala.util.Random

/** Random bot strategy for Dice Chess.
  *
  * Selects a uniformly random legal full-turn path from all candidates produced by [[TurnGenerator]]. This
  * implementation is primarily intended for:
  *   - **Testing** — provides an unpredictable opponent without evaluation bias.
  *   - **Fairness baselines** — useful for measuring improvement over pure chance.
  *   - **Move diversity** — can be used in training pipelines to generate varied game states.
  *
  * Uses a module-level [[scala.util.Random]] instance that is *not* seeded, so results are non-deterministic across
  * runs. For reproducible play, prefer [[GreedySearch.findBestMove(state, dice, rand)]] with an explicit seed.
  */
object RandomSearch extends SearchAlgorithm:

  private val rand = new Random()

  /** Finds a random legal move.
    *
    * @param state
    *   current [[GameState]]; `state.activeColor` indicates who is moving
    * @return
    *   a randomly chosen [[ScoredSequence]], or `None` if no legal move exists (forced pass)
    */
  override def findBestMove(state: GameState): Option[ScoredSequence] =
    findBestMove(state, rand)

  /** Finds a random legal move with an explicit `Random` instance.
    *
    * @param state
    *   current [[GameState]]; `state.activeColor` indicates who is moving
    * @param random
    *   random number generator used for selection
    * @return
    *   a randomly chosen [[ScoredSequence]], or `None` if no legal move exists (forced pass)
    */
  def findBestMove(state: GameState, random: Random): Option[ScoredSequence] =
    val paths = TurnGenerator.generateAllLegalTurnPaths(state)
    if paths.isEmpty then None
    else
      val randomPath = paths(random.nextInt(paths.length))
      Some(SearchScoring.scorePath(state, randomPath))
