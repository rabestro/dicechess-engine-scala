package dicechess.engine.search

import dicechess.engine.domain.GameState
import scala.util.Random

/** Capability mix-in for a [[SearchAlgorithm]] that can search under a wall-clock deadline.
  *
  * A plain [[SearchAlgorithm]] runs to its own internal budget (a fixed rollout or node count); a `TimeBudgetedSearch`
  * additionally accepts an external [[java.lang.System.nanoTime]] deadline and is expected to stop at it, always
  * returning a legal fallback move when one exists. This lets a caller on a game clock (e.g. the JS API's
  * `timeBudgetMs`) bound per-move thinking time without knowing the algorithm's internals.
  *
  * The deadline path is non-deterministic by design (it depends on machine speed), so it is meant for play rather than
  * reproducible tests.
  */
trait TimeBudgetedSearch extends SearchAlgorithm:

  /** Finds the best full-turn path, stopping at `deadlineNanos` (a [[java.lang.System.nanoTime]] value).
    *
    * @param state
    *   the current [[GameState]]; `state.activeColor` identifies the side to move
    * @param deadlineNanos
    *   a [[java.lang.System.nanoTime]] value; the search stops once it is reached
    * @param random
    *   the random source for the (non-deterministic) search
    * @return
    *   `Some([[ScoredSequence]])` when at least one legal path exists — even if the deadline elapses before the search
    *   completes — or `None` when the player must pass
    */
  def findBestMove(state: GameState, deadlineNanos: Long, random: Random): Option[ScoredSequence]
