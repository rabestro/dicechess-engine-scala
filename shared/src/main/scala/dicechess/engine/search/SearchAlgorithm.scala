package dicechess.engine.search

import dicechess.engine.domain.*

/** Value object returned by search implementations so callers can keep move-path and evaluation bound together across
  * API boundaries.
  */
case class ScoredSequence(moves: List[Move], score: Int)

trait SearchAlgorithm:
  /** Search contract used by bot strategies.
    *
    * Implementations rank legal turn paths and return the preferred result, or `None` when no legal action exists for
    * the rolled dice.
    *
    * @param state
    *   current [[GameState]]
    * @param dice
    *   available die faces (1-6) for this turn
    * @return
    *   `Some([[ScoredSequence]])` when at least one legal path exists; otherwise `None`.
    */
  def findBestMove(state: GameState, dice: List[Int]): Option[ScoredSequence]

object SearchScoring:
  val TerminalWinScore: Int = Int.MaxValue

  def scorePath(state: GameState, path: List[Move]): ScoredSequence =
    val score =
      if isKingCapturePath(state, path) then TerminalWinScore
      else
        val finalState = if path.isEmpty then state
        else {
          val initMoves         = path.init
          val intermediateState = initMoves.foldLeft(state)((s, m) => s.makeMove(m).copy(activeColor = s.activeColor))
          intermediateState.makeMove(path.last)
        }
        Evaluator.evaluateMaterial(finalState, state.activeColor)
    ScoredSequence(path, score)

  private def isKingCapturePath(initialState: GameState, path: List[Move]): Boolean =
    if path.isEmpty then false
    else
      val activeColor     = initialState.activeColor
      val stateBeforeLast = path.init.foldLeft(initialState) { (state, move) =>
        state.makeMove(move).copy(activeColor = activeColor)
      }
      stateBeforeLast.mailbox
        .get(path.last.toSquare)
        .exists(piece => piece.pieceType == PieceType.King && piece.color != activeColor)
