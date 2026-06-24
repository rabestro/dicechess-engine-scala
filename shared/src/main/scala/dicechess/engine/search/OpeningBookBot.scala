package dicechess.engine.search

import dicechess.engine.domain.*
import scala.util.Random

/** A decorator that consults an opening book before falling back to the wrapped algorithm.
  *
  * The `book` maps a canonical key (see [[OpeningBook.key]]) to the chosen continuation, a comma-separated list of
  * long-algebraic micro-moves (e.g. `"e2e4,f1c4"`). When the current position's key is present, the decorator plays the
  * booked turn instantly — without spending the underlying bot's search budget — which is exactly where a heuristic or
  * rollout-based engine is weakest and where empirical data is richest. Otherwise it delegates to `underlying`.
  *
  * The booked moves are matched against the position's legal turn paths by move **multiset**, so the order stored in
  * the book does not matter and an entry that cannot be realised legally (a stale or corrupt book) is silently ignored
  * in favour of the underlying bot rather than played illegally.
  *
  * @param underlying
  *   the bot to consult when the position is not booked; may be any [[SearchAlgorithm]] (time-budgeted or not)
  * @param book
  *   the opening book, keyed per [[OpeningBook.key]]
  */
class OpeningBookBot(val underlying: SearchAlgorithm, val book: Map[String, String])
    extends SearchAlgorithm
    with TimeBudgetedSearch:

  override def findBestMove(state: GameState): Option[ScoredSequence] =
    lookupMove(state).orElse(underlying.findBestMove(state))

  /** Plays the booked move when present; otherwise forwards the deadline to a time-budgeted underlying bot, or falls
    * back to its unbounded search when it is not time-budgeted.
    */
  override def findBestMove(state: GameState, deadlineNanos: Long, random: Random): Option[ScoredSequence] =
    lookupMove(state).orElse {
      underlying match
        case tb: TimeBudgetedSearch => tb.findBestMove(state, deadlineNanos, random)
        case _                      => underlying.findBestMove(state)
    }

  override def shouldOfferDouble(state: GameState, currentStake: Int): Boolean =
    underlying match
      case drawLogic: DrawOfferLogic => drawLogic.shouldOfferDouble(state, currentStake)
      case _                         => false

  override def shouldAcceptDouble(state: GameState, currentStake: Int): Boolean =
    underlying match
      case drawLogic: DrawOfferLogic => drawLogic.shouldAcceptDouble(state, currentStake)
      case _                         => false

  override def shouldOfferDraw(state: GameState): Boolean =
    underlying match
      case drawLogic: DrawOfferLogic => drawLogic.shouldOfferDraw(state)
      case _                         => false

  override def shouldAcceptDraw(state: GameState): Boolean =
    underlying match
      case drawLogic: DrawOfferLogic => drawLogic.shouldAcceptDraw(state)
      case _                         => false

  /** Long-algebraic notation of a micro-move including any promotion suffix (e.g. `"e2e4"`, `"e7e8q"`). */
  private def uci(move: Move): String =
    move.fromSquare.toNotation + move.toSquare.toNotation + move.promotionPieceType.fold("")(_.asNotation)

  /** Multiset signature of a move sequence: its UCI tokens sorted and joined, so two orderings of the same turn compare
    * equal.
    */
  private def signature(moves: List[String]): String =
    moves.sorted.mkString(",")

  private def lookupMove(state: GameState): Option[ScoredSequence] =
    for
      key       <- OpeningBook.key(state)
      bookMoves <- book.get(key)
      target = signature(bookMoves.split(",").iterator.map(_.trim).filter(_.nonEmpty).toList)
      path <- TurnGenerator
        .generateAllLegalTurnPaths(state)
        .find(p => signature(p.map(uci)) == target)
    yield SearchScoring.scorePath(state, path)
