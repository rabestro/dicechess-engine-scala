package dicechess.engine.search

import dicechess.engine.domain.*

import scala.util.Random

/** Tuning for [[ExpectimaxSearch]].
  *
  * @param candidateLimit
  *   how many of the mover's own turns (pre-ranked cheaply by material) are expanded to full depth. Dice Chess
  *   routinely offers hundreds of legal turns per roll, so expanding all of them through a chance node is infeasible;
  *   this bounds the branching at the decision node. Must be positive.
  */
final case class ExpectimaxConfig(candidateLimit: Int = 8):
  require(candidateLimit > 0, s"candidateLimit must be positive, got $candidateLimit")

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
  * Depth is fixed at two plies; the per-move deadline and the batched-model wiring arrive with the model-backed bot.
  */
final class ExpectimaxSearch(
    evalBatch: (Array[GameState], Color) => Array[Int],
    config: ExpectimaxConfig = ExpectimaxConfig()
) extends SearchAlgorithm:

  import ExpectimaxSearch.*

  override def findBestMove(state: GameState): Option[ScoredSequence] =
    findBestMove(state, new Random())

  /** Finds the best turn with an explicit `Random` for reproducible tie-breaking among equally-valued turns. */
  def findBestMove(state: GameState, rand: Random): Option[ScoredSequence] =
    val myColor = state.activeColor
    val paths   = TurnGenerator.generateAllLegalTurnPaths(state)
    if paths.isEmpty then None
    else
      // An immediate king capture wins now; never let material pre-ranking prune it (the king has no material value).
      val winning = paths.filter(path => capturesEnemyKing(state, path))
      if winning.nonEmpty then Some(ScoredSequence(winning.minBy(_.size), SearchScoring.TerminalWinScore))
      else
        // Pre-rank cheaply by material, expand only the top candidates through the (expensive) chance node.
        val candidates = paths
          .sortBy(path => -SearchScoring.scorePath(state, path, Evaluator.evaluateMaterial).score)
          .take(config.candidateLimit)
        val scored = candidates.map(path => path -> turnValue(state, path, myColor))
        val bestQ  = scored.map(_._2).max
        val best   = scored.collect { case (path, q) if q == bestQ => path }
        Some(ScoredSequence(best(rand.nextInt(best.length)), bestQ.toInt))

  /** The expectimax value of playing `path`: apply it, hand the turn over, and average the opponent's best reply across
    * every possible roll.
    */
  private def turnValue(state: GameState, path: List[Move], myColor: Color): Double =
    chanceNodeValue(applyTurn(state, path), myColor)

  /** The expectation, over the 56 weighted dice outcomes, of the opponent's best reply value (from `myColor`'s view).
    *
    * @param oppToMove
    *   position after our turn: the opponent is to move and the dice pool is empty.
    */
  private def chanceNodeValue(oppToMove: GameState, myColor: Color): Double =
    var acc = 0.0
    var i   = 0
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
      acc += (weight.toDouble / DiceRolls.totalOrderedRolls) * rollValue
      i += 1
    acc

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
