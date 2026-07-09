package dicechess.engine.search

import dicechess.engine.domain.GameState

import scala.util.Random

/** A 2-ply expectimax bot whose leaf evaluator is an externally-trained model (LightGBM, via ONNX).
  *
  * This is the payoff of the search work: [[ExpectimaxSearch]] supplies the lookahead (my turn, the opponent's roll,
  * the opponent's best reply) and [[OnnxEvalSearch]] supplies the value function, evaluated in batches so all the
  * leaves under one chance node cost a single inference call. It fixes the one-ply model bot's recapture blindness —
  * the same model, but now it sees the reply.
  *
  * Owns the ONNX session (through the wrapped [[OnnxEvalSearch]]); call [[close]] when done. Not safe for concurrent
  * calls, matching every other bot here.
  */
final class OnnxExpectimaxSearch(modelPath: String, config: ExpectimaxConfig = ExpectimaxConfig())
    extends TimeBudgetedSearch
    with AutoCloseable:

  private val onnx       = new OnnxEvalSearch(modelPath)
  private val expectimax = new ExpectimaxSearch((states, color) => onnx.onnxEvalBatch(states, color), config)

  override def findBestMove(state: GameState): Option[ScoredSequence] =
    expectimax.findBestMove(state)

  override def findBestMove(state: GameState, random: Random): Option[ScoredSequence] =
    expectimax.findBestMove(state, random)

  override def findBestMove(state: GameState, deadlineNanos: Long, random: Random): Option[ScoredSequence] =
    expectimax.findBestMove(state, deadlineNanos, random)

  override def close(): Unit = onnx.close()
