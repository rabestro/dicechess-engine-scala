package dicechess.engine.search

import dicechess.engine.domain.*
import dicechess.engine.movegen.MoveGenerator
import scala.util.boundary, boundary.break

/** Probabilistic king (and queen) capture analysis for Dice Chess.
  *
  * Given a position, enumerates all 216 possible 3d6 outcomes (grouped into 56 weighted multisets) and determines
  * whether the opponent can capture the defender's king (or queen) on their next turn.
  *
  * King-capture paths are always legal regardless of the Maximum Micro-moves Rule, so a depth-first search with early
  * exit yields exact probabilities. Queen-capture paths respect the Maximum Micro-moves Rule; the returned probability
  * may therefore be slightly overestimated in edge cases where a queen capture is not part of any max-length sequence.
  */
object KingCaptureProbability:

  /** Pre‑computed unique dice‑roll multisets with their occurrence weights.
    *
    * | Pattern | Example   | Weight | Count |
    * |:--------|:----------|-------:|------:|
    * | AAA     | `[1,1,1]` |      1 |     6 |
    * | AAB     | `[1,1,2]` |      3 |    30 |
    * | ABC     | `[1,2,3]` |      6 |    20 |
    *
    * Total: 6×1 + 30×3 + 20×6 = 216 ordered rolls.
    */
  private val weightedRolls: Array[(List[Int], Int)] =
    val seen = collection.mutable.Set.empty[List[Int]]
    val b    = List.newBuilder[(List[Int], Int)]
    for d1 <- 1 to 6; d2 <- 1 to 6; d3 <- 1 to 6 do {
      val ms = List(d1, d2, d3).sorted
      if seen.add(ms) then
        val weight = ms.distinct.size match
          case 1 => 1
          case 2 => 3
          case _ => 6
        b += ((ms, weight))
    }
    b.result().toArray

  private val TotalRolls: Double = 216.0

  /** Returns the probability `[0.0, 1.0]` that the opponent can capture the defender's king on their next turn. */
  def kingCaptureProbability(state: GameState, defenderColor: Color): Double =
    captureProbability(state, defenderColor, state.kings)

  /** Returns the probability `[0.0, 1.0]` that the opponent can capture a defender's queen on their next turn.
    *
    * This method applies the same DFS used for king capture and may slightly overestimate the true probability in edge
    * cases where a queen capture is not part of any max‑length micro‑move sequence (Maximum Micro‑moves Rule).
    */
  def queenCaptureProbability(state: GameState, defenderColor: Color): Double =
    captureProbability(state, defenderColor, state.queens)

  private def captureProbability(state: GameState, defenderColor: Color, targetBB: Bitboard): Double = boundary {
    val defenderPieces = if defenderColor.isWhite then state.whitePieces else state.blackPieces
    val targets        = targetBB & defenderPieces
    if targets.isEmpty then break(0.0)

    val opponent = defenderColor.opponent
    var count    = 0
    var i        = 0
    while i < weightedRolls.length do
      val (rolls, weight) = weightedRolls(i)
      val testState       = state.withActiveColor(opponent).withDicePool(rolls)
      if captureDFS(testState, targets) then count += weight
      i += 1
    count / TotalRolls
  }

  /** Depth‑first search over all micro‑move sequences.
    *
    * Returns `true` as soon as '''any''' move in any sequence lands on a square in `targets`. Because king‑capture
    * paths are always legal regardless of the Maximum Micro‑moves Rule, an early exit is correct for kings. For queens
    * the result may slightly overestimate the true probability.
    */
  private def captureDFS(state: GameState, targets: Bitboard): Boolean = boundary {
    if state.dicePool.isEmpty then break(false)
    val moves = MoveGenerator.generateMoves(state)
    var i     = 0
    while i < moves.length do
      val move = moves(i)

      // Direct capture of a target piece
      if !(targets & Bitboard.fromSquare(move.toSquare)).isEmpty then break(true)

      // Recurse — the dice the move consumes depends on whether it is castling
      val nextState =
        if move.isCastling then
          if state.dicePool.contains(PieceType.King.diceValue) && state.dicePool.contains(PieceType.Rook.diceValue) then
            val afterPool = state.dicePool.diff(List(PieceType.King.diceValue, PieceType.Rook.diceValue))
            Some(state.makeMove(move).withDicePool(afterPool))
          else None
        else
          val moverType = state.mailbox(move.fromSquare).pieceType
          val afterPool = state.dicePool.diff(List(moverType.diceValue))
          Some(state.makeMove(move).withDicePool(afterPool))

      nextState match
        case Some(s) => if captureDFS(s, targets) then break(true)
        case None    => ()

      i += 1
    false
  }
