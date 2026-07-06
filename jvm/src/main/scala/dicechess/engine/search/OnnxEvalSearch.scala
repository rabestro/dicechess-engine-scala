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
class OnnxEvalSearch(modelPath: String) extends SearchAlgorithm with AutoCloseable:

  private val env     = OrtEnvironment.getEnvironment
  private val session = env.createSession(modelPath, new OrtSession.SessionOptions())

  /** Runs the ONNX session on `state`'s material features from `color`'s perspective, scaled onto `scorePath`'s Int
    * axis. The model's raw output is a probability in [0, 1]; ×10000 keeps it far below
    * [[SearchScoring.TerminalWinScore]] (`Int.MaxValue`) while preserving resolution to discriminate between close
    * positions.
    */
  def onnxEval(state: GameState, color: Color): Int =
    val features    = OnnxFeatures.extract(state, color)
    val inputTensor = OnnxTensor.createTensor(env, Array(features))
    try
      val result = session.run(Collections.singletonMap("input", inputTensor))
      try (result.get(0).getValue.asInstanceOf[Array[Array[Float]]](0)(0) * 10000).toInt
      finally result.close()
    finally inputTensor.close()

  override def findBestMove(state: GameState): Option[ScoredSequence] =
    findBestMove(state, new Random())

  def findBestMove(state: GameState, rand: Random): Option[ScoredSequence] =
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
