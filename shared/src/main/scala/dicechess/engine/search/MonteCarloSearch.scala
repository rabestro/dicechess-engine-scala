package dicechess.engine.search

import dicechess.engine.domain.*
import scala.util.Random

/** Monte-Carlo search bot (difficulty 7).
  *
  * Ranks every legal full-turn path by the Rao-Blackwellized Monte-Carlo win probability of the *resulting* position
  * ([[MonteCarloEquity]]) and plays the highest-scoring one; an immediate king capture is always preferred. Where
  * [[PrudentSearch]] scores only a single ply of king-capture risk, this estimates the full-game win probability via
  * rollouts, so it accounts for deeper consequences.
  *
  * Per-move cost scales with the number of legal turns × the rollout budget, so [[DefaultConfig]] uses a modest budget.
  * Tests and benchmarks pass an explicit [[MonteCarloConfig]] and [[scala.util.Random]] through
  * [[findBestMove(state:dicechess\.engine\.domain\.GameState,config:dicechess\.engine\.search\.MonteCarloConfig,random:scala\.util\.Random)*]]
  * for reproducible, faster, or stronger play.
  */
object MonteCarloSearch extends SearchAlgorithm with DrawOfferLogic:

  /** Maps a win probability in `[0, 1]` onto the integer [[ScoredSequence.score]]. Kept far below
    * [[SearchScoring.TerminalWinScore]] (`Int.MaxValue`) so a king capture always outranks any probabilistic score.
    */
  private val ProbScoreScale = 1_000_000

  /** Default per-move budget — a balance between playing strength and decision latency. */
  val DefaultConfig: MonteCarloConfig = MonteCarloConfig(rollouts = 120, maxPlies = 40)

  private val rand = new Random()

  override def findBestMove(state: GameState): Option[ScoredSequence] =
    findBestMove(state, DefaultConfig, rand)

  /** Finds the best turn under an explicit rollout budget and random source. */
  def findBestMove(state: GameState, config: MonteCarloConfig, random: Random): Option[ScoredSequence] =
    val paths = TurnGenerator.generateAllLegalTurnPaths(state)
    if paths.isEmpty then None
    else
      val scoredPaths  = paths.map(path => SearchScoring.scorePath(state, path, equityEval(config, random)))
      val maxCriterion = scoredPaths.map(s => (s.score, terminalWinPreference(s))).max
      val optimalPaths = scoredPaths.filter(s => (s.score, terminalWinPreference(s)) == maxCriterion)
      Some(optimalPaths(random.nextInt(optimalPaths.length)))

  /** Among equal-scoring paths, prefer a shorter king capture (faster win); irrelevant for non-terminal scores. */
  private def terminalWinPreference(scored: ScoredSequence): Int =
    if scored.score == SearchScoring.TerminalWinScore then -scored.moves.size else 0

  /** Evaluation handed to [[SearchScoring.scorePath]]: the Monte-Carlo win probability of the post-turn position from
    * the moving side's perspective, scaled to an integer. `scorePath` short-circuits king captures to
    * [[SearchScoring.TerminalWinScore]] before this is called, so rollouts run only on non-terminal continuations.
    */
  private def equityEval(config: MonteCarloConfig, random: Random)(state: GameState, color: Color): Int =
    val est     = MonteCarloEquity.estimate(state, config, random)
    val winProb = if color.isWhite then est.whiteWin else est.blackWin
    (winProb * ProbScoreScale).round.toInt

  /** Uses the Monte-Carlo estimate (rather than the material sigmoid) for doubling-cube decisions. */
  override protected def estimateWinProbability(state: GameState): Double =
    val est = MonteCarloEquity.estimate(state, DefaultConfig, rand)
    if state.activeColor.isWhite then est.whiteWin else est.blackWin

  override def shouldOfferDouble(state: GameState, currentStake: Int): Boolean =
    val _ = currentStake
    estimateWinProbability(state) > 0.65

  override def shouldAcceptDouble(state: GameState, currentStake: Int): Boolean =
    val _ = currentStake
    estimateWinProbability(state) > 0.30
