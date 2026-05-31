package dicechess.engine.search

import dicechess.engine.domain.*
import scala.util.Random

/** Prudent search algorithm for Dice Chess (difficulty 6).
  *
  * Like [[GreedySearchV2]], evaluates all legal turn paths and scores them by material and king safety. In addition, it
  * penalises paths that leave the king (or queen) exposed to capture on the opponent's next turn using a probabilistic
  * model that accounts for all possible dice rolls (3d6).
  *
  * The evaluation function is:
  * {{{
  *   score = material
  *           - P(king capture next turn) × 3000
  *           - P(queen capture next turn) × 600
  * }}}
  *
  * This creates a smooth gradient: a move that leaves the king vulnerable in 10 % of rolls (penalty −300) is preferred
  * over one that leaves it vulnerable in 100 % of rolls (penalty −3000), unlike the binary `evaluateKingSafety` which
  * applies a flat −2000 regardless of probability.
  */
object PrudentSearch extends SearchAlgorithm:

  private val KingCaptureWeight  = 3000
  private val QueenCaptureWeight = 600

  private val rand = new Random()

  override def findBestMove(state: GameState): Option[ScoredSequence] =
    findBestMove(state, rand)

  def findBestMove(state: GameState, random: Random): Option[ScoredSequence] =
    val paths = TurnGenerator.generateAllLegalTurnPaths(state)
    if paths.isEmpty then None
    else
      val scoredPaths  = paths.map(path => SearchScoring.scorePath(state, path, evalWithCaptureProbability))
      val maxCriterion = scoredPaths.map(s => (s.score, terminalWinPreference(s))).max
      val optimalPaths = scoredPaths.filter(s => (s.score, terminalWinPreference(s)) == maxCriterion)
      Some(optimalPaths(random.nextInt(optimalPaths.length)))

  private def terminalWinPreference(scored: ScoredSequence): Int =
    if scored.score == SearchScoring.TerminalWinScore then -scored.moves.size else 0

  private def evalWithCaptureProbability(state: GameState, color: Color): Int =
    Evaluator.evaluateMaterial(state, color) -
      (KingCaptureProbability.kingCaptureProbability(state, color) * KingCaptureWeight).toInt -
      (KingCaptureProbability.queenCaptureProbability(state, color) * QueenCaptureWeight).toInt

  override def shouldOfferDouble(state: GameState, currentStake: Int): Boolean =
    val _ = currentStake
    estimateWinProbability(state) > 0.65

  override def shouldAcceptDouble(state: GameState, currentStake: Int): Boolean =
    val _ = currentStake
    estimateWinProbability(state) > 0.30
