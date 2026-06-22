package dicechess.engine.search

import dicechess.engine.domain.*
import scala.util.Random

/** Decorator that queries an opening book before falling back to the underlying algorithm.
  *
  * The `book` maps a serialized position key to a sequence of moves (e.g. "e2e4,f1c4"). The key is formed as exactly
  * `FenParser.serialize(state)` which naturally includes the dice pool.
  *
  * This allows the bot to instantly play statistically proven opening moves without consuming its search time budget.
  * If the position is not in the book, it delegates to the `underlying` bot.
  */
class OpeningBookBot(val underlying: SearchAlgorithm, val book: Map[String, String])
    extends SearchAlgorithm
    with TimeBudgetedSearch:

  override def findBestMove(state: GameState): Option[ScoredSequence] =
    lookupMove(state) match
      case Some(scoredSeq) => Some(scoredSeq)
      case None            => underlying.findBestMove(state)

  /** If the underlying bot is time-budgeted, the decorator passes the budget through. */
  override def findBestMove(state: GameState, deadlineNanos: Long, random: Random): Option[ScoredSequence] =
    lookupMove(state) match
      case Some(scoredSeq) => Some(scoredSeq)
      case None            =>
        underlying match
          case tb: TimeBudgetedSearch => tb.findBestMove(state, deadlineNanos, random)
          case _                      => underlying.findBestMove(state)

  override def shouldOfferDouble(state: GameState, currentStake: Int): Boolean =
    underlying match
      case drawLogic: DrawOfferLogic => drawLogic.shouldOfferDouble(state, currentStake)
      case _                         => false

  override def shouldAcceptDouble(state: GameState, currentStake: Int): Boolean =
    underlying match
      case drawLogic: DrawOfferLogic => drawLogic.shouldAcceptDouble(state, currentStake)
      case _                         => false

  private def lookupMove(state: GameState): Option[ScoredSequence] =
    if state.dicePool.isEmpty then None
    else
      val key = FenParser.serialize(state)
      book.get(key).flatMap { targetPathStr =>
        val targetMoves = targetPathStr.split(',')
        val paths       = TurnGenerator.generateAllLegalTurnPaths(state)
        paths.find { p =>
          p.length == targetMoves.length &&
          p.zip(targetMoves).forall { case (m, expected) =>
            val fromTo = m.fromSquare.toNotation + m.toSquare.toNotation
            fromTo == expected
          }
        } match
          case Some(path) => Some(ScoredSequence(path, SearchScoring.TerminalWinScore))
          case None       => None // Fallback if the book suggests an illegal move (should not happen)
      }
