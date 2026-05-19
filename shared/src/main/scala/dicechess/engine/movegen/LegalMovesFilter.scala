package dicechess.engine.movegen

import scala.util.boundary
import scala.util.boundary.break
import dicechess.engine.domain.*

/** Legal moves filter for Dice Chess.
  *
  * Implements the **Maximum Micro-moves Rule**: a player must choose a first move that is part of the longest possible
  * sequence of micro-moves achievable with the rolled dice. Shorter sequences are illegal unless they include a
  * King-Capture (win condition).
  *
  * ## Rules encoded here
  *   - **Normal move** — consumes exactly one die of the matching piece type.
  *   - **Castling** — requires *and* consumes *both* the King die (`6`) and the Rook die (`4`) simultaneously.
  *   - **King-Capture Exemption** — any move that directly captures the opponent's King is immediately legal,
  *     regardless of whether it achieves the maximum sequence length.
  *   - **Active-color invariance** — the active color is kept fixed for every intermediate state produced during a
  *     turn; it is *not* toggled between micro-moves.
  */
object LegalMovesFilter:

  // ── Private helpers ──────────────────────────────────────────────────────────

  /** Returns `true` when `move` captures the opponent's King (win condition). */
  private def isKingCapture(state: GameState, move: Move): Boolean =
    state.mailbox
      .get(move.toSquare)
      .exists(p => p.pieceType == PieceType.King && p.color != state.activeColor)

  /** Recursively computes the maximum achievable micro-move sequence length from `state` with `remainingDice`.
    *
    * The search is bounded by the depth of `remainingDice` (at most 3), so it terminates in finite time. The active
    * color is intentionally kept fixed — `makeMove` normally toggles it, so we restore it with `.copy` after each
    * recursive step.
    *
    * Uses `scala.util.boundary` to short-circuit on King-Capture (win condition).
    *
    * @param state
    *   the board position to evaluate (active color unchanged throughout the turn)
    * @param remainingDice
    *   the dice still available to spend in this turn (multiset)
    * @return
    *   the maximum number of micro-moves reachable from `state` using `remainingDice`
    */
  private def maxSequenceLength(state: GameState, remainingDice: List[Int]): Int =
    if remainingDice.isEmpty then 0
    else
      boundary[Int]:
        val activeColor = state.activeColor
        var best        = 0

        for d <- remainingDice.distinct do
          for move <- MoveGenerator.generateMoves(state, d) do
            if isKingCapture(state, move) then
              // Win condition — short-circuit the entire search immediately
              break(1)
            else if move.isCastling then
              // Castling requires BOTH King (6) and Rook (4) dice to be present
              if remainingDice.contains(PieceType.King.diceValue) &&
                remainingDice.contains(PieceType.Rook.diceValue)
              then
                val afterCastle = remainingDice.diff(List(PieceType.King.diceValue, PieceType.Rook.diceValue))
                val next        = state.makeMove(move).copy(activeColor = activeColor)
                val depth       = 2 + maxSequenceLength(next, afterCastle)
                if depth > best then best = depth
            else
              val afterMove = remainingDice.diff(List(d))
              val next      = state.makeMove(move).copy(activeColor = activeColor)
              val depth     = 1 + maxSequenceLength(next, afterMove)
              if depth > best then best = depth

        best

  // ── Public API ────────────────────────────────────────────────────────────────

  /** Filters and returns the legal first moves for a given position and rolled dice.
    *
    * A first move is legal if and only if one of the following holds:
    *   1. **King-Capture Exemption** — the move directly captures the opponent's King.
    *   2. **Maximum-length condition** — the move is part of a path whose total length equals the globally optimal
    *      sequence length `L*(state, dice)`.
    *
    * When no moves are achievable at all (all rolled dice correspond to piece types absent from the board), an empty
    * list is returned and the player must pass their turn.
    *
    * @param state
    *   the current game state (active color indicates whose turn it is)
    * @param dice
    *   the list of dice rolls for this turn (multiset, values in `[1, 6]`)
    * @return
    *   the list of legal first micro-moves under the Maximum Micro-moves Rule
    */
  def filterMaximalMoves(state: GameState, dice: List[Int]): List[Move] =
    if dice.isEmpty then Nil
    else
      val activeColor = state.activeColor

      // Pass 1: determine the globally optimal sequence length from this position
      val maxLen = maxSequenceLength(state, dice)

      // If no sequence is achievable (all dice unplayable), the player passes
      if maxLen == 0 then Nil
      else
        // Pass 2: collect first moves that achieve maxLen, plus any king captures
        val result = List.newBuilder[Move]

        for d <- dice.distinct do
          for move <- MoveGenerator.generateMoves(state, d) do
            if isKingCapture(state, move) then result += move
            else if move.isCastling then
              if dice.contains(PieceType.King.diceValue) &&
                dice.contains(PieceType.Rook.diceValue)
              then
                val afterCastle = dice.diff(List(PieceType.King.diceValue, PieceType.Rook.diceValue))
                val next        = state.makeMove(move).copy(activeColor = activeColor)
                val reachable   = 2 + maxSequenceLength(next, afterCastle)
                if reachable == maxLen then result += move
            else
              val afterMove = dice.diff(List(d))
              val next      = state.makeMove(move).copy(activeColor = activeColor)
              val reachable = 1 + maxSequenceLength(next, afterMove)
              if reachable == maxLen then result += move

        result.result()
