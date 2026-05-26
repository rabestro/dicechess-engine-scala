package dicechess.engine.search

import dicechess.engine.domain.*

/** The scored result of a full-turn path evaluation.
  *
  * @param moves
  *   the sequence of 1–3 micro-moves that make up the turn; never empty for a valid result
  * @param score
  *   material score from the perspective of the side that played the turn. Use [[SearchScoring.TerminalWinScore]] to
  *   signal a King capture (win condition).
  */
case class ScoredSequence(moves: List[Move], score: Int)

/** Contract for bot strategies in the Dice Chess Engine.
  *
  * Implementations receive the current [[GameState]] and the multiset of available dice rolls, then return the chosen
  * full-turn path wrapped in a [[ScoredSequence]], or `None` when the position has no legal moves (the active player
  * must pass).
  *
  * Implementations are expected to be thread-safe singletons (e.g., Scala `object`).
  */
trait SearchAlgorithm:
  /** Finds and returns the best full-turn path according to this strategy.
    *
    * @param state
    *   the current [[GameState]]; `state.activeColor` identifies the side to move
    * @return
    *   `Some([[ScoredSequence]])` when at least one legal path exists; `None` when the player must pass
    */
  def findBestMove(state: GameState): Option[ScoredSequence]

/** Shared scoring utilities used by all [[SearchAlgorithm]] implementations.
  *
  * Centralises the terminal-win sentinel and the path-scoring logic so that every strategy applies them consistently.
  */
object SearchScoring:
  /** Sentinel score assigned to any path that ends with a King capture.
    *
    * Using `Int.MaxValue` guarantees that any winning path outscores all material-only evaluations. Strategies that
    * prefer *shorter* wins over *longer* ones must compare path lengths separately (see [[GreedySearch]]).
    */
  val TerminalWinScore: Int = Int.MaxValue

  /** Evaluates a full-turn path and returns a [[ScoredSequence]].
    *
    * The path is replayed move by move, preserving the active color between micro-moves (Dice Chess rule). If the final
    * move captures the opponent's King, the score is set to [[TerminalWinScore]]; otherwise
    * [[Evaluator.evaluateMaterial]] is called on the resulting position from the perspective of the side that played
    * the turn.
    *
    * @param state
    *   the position *before* the turn is played; `state.activeColor` is the side to move
    * @param path
    *   the sequence of moves to evaluate; may be empty (yields material score of the current position)
    * @return
    *   a [[ScoredSequence]] bundling `path` and its computed score
    */
  def scorePath(state: GameState, path: List[Move]): ScoredSequence =
    val score =
      if path.isEmpty then Evaluator.evaluateMaterial(state, state.activeColor)
      else
        val activeColor       = state.activeColor
        val intermediateState = path.init.foldLeft(state)((s, m) => s.makeMove(m).withActiveColor(activeColor))
        val lastMove          = path.last
        val isKingCapture     = intermediateState.mailbox
          .get(lastMove.toSquare)
          .exists(piece => piece.pieceType == PieceType.King && piece.color != activeColor)

        if isKingCapture then TerminalWinScore
        else
          val finalState = intermediateState.makeMove(lastMove)
          Evaluator.evaluateMaterial(finalState, activeColor)
    ScoredSequence(path, score)
