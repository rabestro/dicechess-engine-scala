package dicechess.engine.search

import dicechess.engine.domain.*

import scala.util.Random

/** Tuning for [[ExpectimaxSearch]].
  *
  * @param candidateLimit
  *   how many of the mover's own turns (pre-ranked by [[ExpectimaxSearch]]'s `preRank`, material by default) are
  *   expanded to full depth. Dice Chess routinely offers hundreds of legal turns per roll, so expanding all of them
  *   through a chance node is infeasible; this bounds the branching at the decision node. Must be positive.
  */
final case class ExpectimaxConfig(candidateLimit: Int = 8):
  require(candidateLimit > 0, s"candidateLimit must be positive, got $candidateLimit")

/** Root-level rescoring: after the search's own chance-node expectation is computed for each root candidate, blend it
  * with a second, cheaper-at-the-root evaluator run *once* on the candidates' own resulting positions (before the
  * opponent's roll) — `finalScore = (1 - weight) * searchValue + weight * rescoreValue`.
  *
  * Meant for a tactically sharp but leaf-prohibitive evaluator: e.g. king/queen capture-probability features cost a
  * 216-outcome DFS each, far too expensive under a chance node's hundreds of leaves, but cheap enough for the handful
  * of root candidates ([[ExpectimaxConfig.candidateLimit]]).
  *
  * A candidate where at least one dice roll loses our king outright ([[ExpectimaxSearch.LossValue]]) is never rescored,
  * at any weight — that sentinel must always rank last regardless of what the rescorer thinks of the resulting position
  * (see `findBestMove`'s loss-taint tracking).
  *
  * @param evalBatch
  *   the rescoring evaluator, same batching contract as the search's own `evalBatch`
  * @param weight
  *   blend weight; must be in `(0, 1]` (0 would be indistinguishable from omitting rescoring entirely, so `None` is the
  *   only way to express "disabled")
  */
final case class RootRescore(evalBatch: (Array[GameState], Color) => Array[Int], weight: Double):
  require(weight > 0.0 && weight <= 1.0, s"weight must be in (0, 1], got $weight")

/** Two-ply expectimax search for Dice Chess: my turn, then the opponent's dice roll, then the opponent's best reply.
  *
  * Unlike a one-ply evaluator ([[GreedySearch]], [[OnnxEvalSearch]]), this looks one full turn ahead and so sees
  * tactical punishments — a capture that hangs a bigger piece to the recapture — that a static evaluation cannot. The
  * layer between the two plies is a **chance node**: the opponent's roll is unknown when we move, so the value of our
  * turn is the expectation over all [[DiceRolls]] outcomes of the opponent's best (for them) reply.
  *
  * The evaluation function is injected as a batch (`evalBatch(states, color)` scores every state from `color`'s
  * perspective) so the same search works with any leaf evaluator — the engine's material score, or an externally
  * trained model — and so the many leaves under one chance node can be scored in a single call. Leaf scores are only
  * ever compared, minimised, and averaged, so the search is agnostic to the evaluator's absolute scale.
  *
  * Two terminal cases sit outside the evaluator, because a king capture ends the game and material scores never see the
  * king:
  *   - if one of our own turns captures the opponent's king, we play it immediately (an outright win);
  *   - a leaf where the opponent captures our king is worth [[ExpectimaxSearch.LossValue]] — below any real score on
  *     any scale — so the opponent always takes it and we always rank that line last.
  *
  * Depth is fixed at two plies. As a [[TimeBudgetedSearch]] it also honours a wall-clock deadline, expanding pre-ranked
  * candidates until time runs out.
  *
  * @param preRank
  *   batched evaluator used to rank the mover's own legal turns before the (expensive) chance-node expansion — only the
  *   top [[ExpectimaxConfig.candidateLimit]] are explored. Defaults to material ([[ExpectimaxSearch.materialBatch]] —
  *   the historical, hardcoded behaviour). Widening `candidateLimit` compensates for a crude pre-ranker at linear
  *   search-cost growth; a sharper pre-ranker (e.g. the same value model already driving the chance node) attacks the
  *   actual bottleneck instead — candidateLimit=16 vs material pre-ranking measured +4.8pp purely from surfacing turns
  *   the material proxy had buried outside the top 8.
  */
final class ExpectimaxSearch(
    evalBatch: (Array[GameState], Color) => Array[Int],
    config: ExpectimaxConfig = ExpectimaxConfig(),
    rootRescore: Option[RootRescore] = None,
    preRank: (Array[GameState], Color) => Array[Int] = ExpectimaxSearch.materialBatch
) extends TimeBudgetedSearch:

  import ExpectimaxSearch.*

  override def findBestMove(state: GameState): Option[ScoredSequence] =
    findBestMove(state, new Random())

  /** Finds the best turn with an explicit `Random`, running to completion over every candidate. */
  override def findBestMove(state: GameState, rand: Random): Option[ScoredSequence] =
    findBestMove(state, NoDeadline, rand)

  /** Finds the best turn under a wall-clock deadline.
    *
    * Candidates are expanded in material-ranked order; the top one is always evaluated, and the deadline is honoured
    * between candidates thereafter, so the result is the best turn found so far when time runs out (anytime search).
    * This matters because a single roll can generate thousands of opponent replies (the Dice Chess branching tail).
    */
  override def findBestMove(state: GameState, deadlineNanos: Long, random: Random): Option[ScoredSequence] =
    val myColor = state.activeColor
    val paths   = TurnGenerator.generateAllLegalTurnPaths(state)
    if paths.isEmpty then None
    else
      // An immediate king capture wins now; never let pre-ranking prune it (the king has no material/model value).
      val winning = paths.filter(path => capturesEnemyKing(state, path))
      if winning.nonEmpty then Some(ScoredSequence(winning.minBy(_.size), SearchScoring.TerminalWinScore))
      else
        // Every remaining path is provably non-king-capturing (the filter above already removed those), so its own
        // resulting position — needed for both the pre-rank score and, for survivors, the chance node — is exactly
        // `applyTurn(state, path)`; computing it once here and reusing it below avoids replaying the same turn twice.
        // Array throughout (not List): the top-K expansion loop below indexes `candidates(i)`, which must stay O(1),
        // and the batched pipeline avoids intermediate linked-list-node allocations in this per-move hot path.
        val withResultState = paths.map(path => path -> applyTurn(state, path)).toArray
        val preRankScores   = preRank(withResultState.map(_._2), myColor)
        // Pre-rank in one batched call, expand only the top candidates through the (expensive) chance node. sortBy is
        // stable (like List's), so equal-scored candidates keep generation order — the material default stays identical.
        val candidates = withResultState
          .zip(preRankScores)
          .sortBy { case (_, score) => -score }
          .map(_._1)
          .take(config.candidateLimit)
        // Each candidate's own resulting position (before the opponent's roll) is kept alongside its chance-node
        // value: the chance node needs it, and — when a root rescorer is configured — so does the rescore batch,
        // scored once over exactly these states rather than recomputed.
        val evaluated = List.newBuilder[(List[Move], GameState, Double, Boolean)]
        var i         = 0
        var continue  = true
        while i < candidates.length && continue do
          val (path, resultState)  = candidates(i)
          val (value, lossTainted) = chanceNodeValue(resultState, myColor)
          evaluated += ((path, resultState, value, lossTainted))
          i += 1
          // Always finish the top candidate, then stop as soon as the deadline has passed.
          if i < candidates.length && System.nanoTime() >= deadlineNanos then continue = false
        val results = evaluated.result()
        val scores  = rootRescore match
          case None                                   => results.map { case (path, _, value, _) => path -> value }
          case Some(RootRescore(rescoreEval, weight)) =>
            val states   = results.map(_._2).toArray
            val rescored = rescoreEval(states, myColor)
            results.zip(rescored).map { case ((path, _, value, lossTainted), rescoreValue) =>
              // A line where every roll loses our king outright must never be masked by a favorable rescore —
              // LossValue sits below any real evaluator scale precisely so it always ranks last (see RootRescore).
              val blended = if lossTainted then value else (1 - weight) * value + weight * rescoreValue
              path -> blended
            }
        val bestQ = scores.map(_._2).max
        val best  = scores.collect { case (path, q) if q == bestQ => path }
        Some(ScoredSequence(best(random.nextInt(best.length)), bestQ.toInt))

  /** The expectation, over the 56 weighted dice outcomes, of the opponent's best reply value (from `myColor`'s view),
    * alongside whether any single roll forced [[LossValue]] (the opponent capturing our king outright) — tracked
    * precisely per roll (an exact match against the sentinel, not a threshold on the weighted average) so
    * [[RootRescore]] can never rescue a line that is lost on even one roll, however small its weight.
    *
    * @param oppToMove
    *   position after our turn: the opponent is to move and the dice pool is empty.
    */
  private def chanceNodeValue(oppToMove: GameState, myColor: Color): (Double, Boolean) =
    var acc         = 0.0
    var lossTainted = false
    var i           = 0
    while i < DiceRolls.weighted.length do
      val (roll, weight) = DiceRolls.weighted(i)
      val rolled         = oppToMove.withDicePool(roll)
      val replies        = TurnGenerator.generateAllLegalTurnPaths(rolled)
      val rollValue      =
        // A forced pass still ends the opponent's (empty) turn and hands the move back to us, so the leaf is
        // `oppToMove.endTurn()` — our turn — consistent with every other leaf, not `oppToMove` (still their turn).
        // Material is blind to the difference, but an evaluator that reads side-to-move, en passant, or move counters
        // would otherwise score the wrong position.
        if replies.isEmpty then evalOne(oppToMove.endTurn(), myColor)
        else opponentMinValue(rolled, replies, myColor)
      if rollValue == LossValue then lossTainted = true
      acc += (weight.toDouble / DiceRolls.totalOrderedRolls) * rollValue
      i += 1
    (acc, lossTainted)

  /** The opponent picks the reply that is worst for us. A reply capturing our king is worst of all ([[LossValue]]);
    * otherwise the resulting leaves are scored in one batch and the minimum is taken.
    */
  private def opponentMinValue(rolled: GameState, replies: List[List[Move]], myColor: Color): Double =
    if replies.exists(reply => capturesEnemyKing(rolled, reply)) then LossValue
    else
      val leaves = replies.iterator.map(reply => applyTurn(rolled, reply)).toArray
      val scores = evalBatch(leaves, myColor)
      var min    = Int.MaxValue
      var i      = 0
      while i < scores.length do
        if scores(i) < min then min = scores(i)
        i += 1
      min.toDouble

  /** Plays every micro-move of `path` (the active color is preserved within a turn) and ends the turn, yielding the
    * position with the other side to move and an empty dice pool.
    */
  private def applyTurn(base: GameState, path: List[Move]): GameState =
    path.foldLeft(base)((s, move) => s.makeMove(move)).endTurn()

  /** Whether `path` (played by `base.activeColor`) ends by capturing the opposing king — same test [[SearchScoring]]
    * uses, applied to either ply.
    */
  private def capturesEnemyKing(base: GameState, path: List[Move]): Boolean =
    val mover      = base.activeColor
    val beforeLast = path.init.foldLeft(base)((s, move) => s.makeMove(move))
    beforeLast.mailbox.get(path.last.toSquare).exists(p => p.pieceType == PieceType.King && p.color != mover)

  private def evalOne(state: GameState, color: Color): Double =
    evalBatch(Array(state), color)(0).toDouble

object ExpectimaxSearch:

  /** Value of a leaf in which the opponent captures our king. Chosen far below any real evaluation on any scale
    * (material centipawns or a scaled win-probability) so the opponent always prefers it and such a line always ranks
    * last — without tying the search to a particular evaluator's range.
    */
  private val LossValue: Double = -1e9

  /** Sentinel deadline for the un-timed entry points: `System.nanoTime()` never reaches it in practice. */
  private val NoDeadline: Long = Long.MaxValue

  /** Default root pre-ranker: material, applied per state — the search's historical, hardcoded behaviour, now just
    * expressed as a batch so it fits the same injectable shape as any other pre-ranker. `private[search]` (not fully
    * private) so JVM-only wiring in this package (e.g. [[OnnxExpectimaxSearch]]) can fall back to it explicitly.
    */
  private[search] def materialBatch(states: Array[GameState], color: Color): Array[Int] =
    states.map(Evaluator.evaluateMaterial(_, color))
