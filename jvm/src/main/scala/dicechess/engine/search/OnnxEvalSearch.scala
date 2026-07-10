package dicechess.engine.search

import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtSession}
import dicechess.engine.domain.*

import java.util.Collections
import scala.util.Random

/** One-ply search scored by an externally-trained model (LightGBM, exported to ONNX) instead of a hand-tuned heuristic.
  * Structurally identical to [[GreedySearch]]/[[AggressiveSearch]] — the only difference is the `evalFn` handed to
  * [[SearchScoring.scorePath]].
  *
  * The ONNX session is created once per instance (session creation, not inference, is the expensive part) and is not
  * thread-safe to call concurrently from multiple threads without external synchronization — matches every other bot in
  * this codebase, none of which claim thread safety either.
  */
class OnnxEvalSearch(
    modelPath: String,
    extractFeatures: (GameState, Color) => Array[Float] = OnnxFeatures.extract
) extends SearchAlgorithm
    with AutoCloseable:

  private val env     = OrtEnvironment.getEnvironment
  private val session = env.createSession(modelPath, new OrtSession.SessionOptions())

  /** The model's raw output is a probability in [0, 1]; scaling by this keeps every score far below
    * [[SearchScoring.TerminalWinScore]] (`Int.MaxValue`) while preserving enough resolution to discriminate between
    * close positions.
    */
  private val ScoreScale = 10000

  /** Runs the ONNX session on a batch of feature rows (`[batch × F]`, `F` = `extractFeatures`' width, which must match
    * the model's input) and returns the scaled scores in row order.
    *
    * The session run is the shared inference primitive behind [[onnxEval]] and [[onnxEvalBatch]]; both funnel through
    * here so the tensor lifecycle and scaling stay in one place. The output tensor is `[batch × 1]` (one probability
    * per row).
    */
  private def runScaled(features: Array[Array[Float]]): Array[Int] =
    val inputTensor = OnnxTensor.createTensor(env, features)
    try
      val result = session.run(Collections.singletonMap("input", inputTensor))
      try result.get(0).getValue.asInstanceOf[Array[Array[Float]]].map(row => (row(0) * ScoreScale).toInt)
      finally result.close()
    finally inputTensor.close()

  /** Runs the ONNX session on `state`'s features (via `extractFeatures`) from `color`'s perspective, scaled onto
    * `scorePath`'s Int axis.
    */
  def onnxEval(state: GameState, color: Color): Int =
    runScaled(Array(extractFeatures(state, color)))(0)

  /** Batched [[onnxEval]]: evaluates many positions from a single `color`'s perspective in one session run.
    *
    * Session-run cost is dominated by per-call overhead (JNI boundary, graph setup), not by the number of rows, so
    * folding N positions into one `[N × F]` tensor is far cheaper than N separate `[1 × F]` runs. This is the hot path
    * for a multi-leaf search — e.g. an expectimax chance node integrating over the 56 dice outcomes (see
    * [[DiceRolls]]), whose leaves are all scored from the root mover's perspective. Scores come back on the same scaled
    * axis as [[onnxEval]], one per input in order; an empty input yields an empty result.
    */
  def onnxEvalBatch(states: Array[GameState], color: Color): Array[Int] =
    if states.isEmpty then Array.emptyIntArray
    else runScaled(states.map(extractFeatures(_, color)))

  override def findBestMove(state: GameState): Option[ScoredSequence] =
    findBestMove(state, new Random())

  override def findBestMove(state: GameState, rand: Random): Option[ScoredSequence] =
    val paths = TurnGenerator.generateAllLegalTurnPaths(state)
    if paths.isEmpty then None
    else
      val scoredPaths  = paths.map(path => SearchScoring.scorePath(state, path, onnxEval))
      val maxCriterion = scoredPaths.map(scored => (scored.score, terminalWinPreference(scored))).max
      val optimalPaths = scoredPaths.filter(scored => (scored.score, terminalWinPreference(scored)) == maxCriterion)
      Some(optimalPaths(rand.nextInt(optimalPaths.length)))

  private def terminalWinPreference(scored: ScoredSequence): Int =
    if scored.score == SearchScoring.TerminalWinScore then -scored.moves.size else 0

  override def close(): Unit = session.close()
