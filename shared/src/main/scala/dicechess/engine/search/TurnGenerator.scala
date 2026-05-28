package dicechess.engine.search

import dicechess.engine.domain.*
import dicechess.engine.movegen.MoveGenerator

/** Exhaustive turn-path generator for Dice Chess.
  *
  * [[TurnGenerator]] performs a depth-first search over all micro-move sequences achievable with the given dice rolls,
  * and filters the result to only the *legal* paths under the **Maximum Micro-moves Rule**:
  *
  *   - A path is legal if it ends with a King capture (win condition), *or*
  *   - its length equals the globally-optimal maximum achievable length.
  *
  * This object is used by [[SearchAlgorithm]] implementations to obtain the candidate set before scoring.
  *
  * @note
  *   Active color is kept constant throughout the turn — it is *not* toggled between micro-moves. `makeMove` normally
  *   flips the side; we restore it explicitly after each step.
  */
object TurnGenerator:

  /** Generates all legal full-turn paths (sequences of 1 to 3 moves) for the given state.
    *
    * A path is a `List[Move]` of 1–3 micro-moves. An empty list means no legal move exists (the player passes). The
    * filtering guarantees that the returned paths either end with a King capture or share the maximum achievable
    * length.
    *
    * @param state
    *   the current [[GameState]]; `state.activeColor` indicates who is moving
    * @return
    *   a (possibly empty) list of legal full-turn paths; each path contains 1–3 moves
    */
  def generateAllLegalTurnPaths(state: GameState): List[List[Move]] =
    val cleanState = state.clearEnPassant(state.activeColor)
    val allPaths   = generateAllPaths(cleanState).filter(_.nonEmpty)
    if allPaths.isEmpty then Nil
    else
      val maxLen = allPaths.map(_.size).maxOption.getOrElse(0)
      allPaths.filter(p => isKingCapturePath(state, p) || p.size == maxLen)

  /** Returns `true` when `move` captures the opponent's King from `state`. */
  private def isKingCapture(state: GameState, move: Move): Boolean =
    state.mailbox
      .get(move.toSquare)
      .exists(p => p.pieceType == PieceType.King && p.color != state.activeColor)

  /** Returns `true` when the *last* move in `path` captures the opponent's King.
    *
    * Replays all moves except the last to obtain the intermediate state, then delegates to [[isKingCapture]].
    *
    * @param initialState
    *   the position before the turn starts
    * @param path
    *   the candidate move sequence; must be non-empty
    */
  private def isKingCapturePath(initialState: GameState, path: List[Move]): Boolean =
    if path.isEmpty then false
    else
      val stateBeforeLast = path.init.foldLeft(initialState) { (s, m) =>
        s.makeMove(m)
      }
      isKingCapture(stateBeforeLast, path.last)

  /** Recursively enumerates all micro-move paths reachable from `state` using `state.dicePool`.
    *
    * Base case: when `state.dicePool` is empty, returns `List(Nil)` — a single empty path representing a pass.
    *
    * For each distinct die value, all pseudo-legal moves of the matching piece type are explored:
    *   - **King capture** — terminal; recorded as a length-1 path without further recursion.
    *   - **Castling** — requires both King (6) and Rook (4) dice; consumes both dice simultaneously.
    *   - **Normal move** — consumes the matching die, recurses with the reduced dice multiset.
    *
    * If no move can be generated for any die (no piece of the matching type exists), returns `List(Nil)` to signal that
    * this branch is a forced pass.
    *
    * @param state
    *   the position to explore; active color is kept fixed throughout the recursion
    * @return
    *   all reachable paths from this position, each encoded as a `List[Move]` (may include `Nil` for pass)
    */
  private def generateAllPaths(state: GameState): List[List[Move]] =
    if state.dicePool.isEmpty then List(Nil)
    else
      val branches = List.newBuilder[List[Move]]

      for move <- MoveGenerator.generateMoves(state) do
        val moverType = state.mailbox(move.fromSquare).pieceType
        if isKingCapture(state, move) then branches += List(move)
        else if move.isCastling then
          if state.dicePool.contains(PieceType.King.diceValue) && state.dicePool.contains(PieceType.Rook.diceValue) then
            // Castling consumes both King and Rook dice
            val afterCastle = state.dicePool.diff(List(PieceType.King.diceValue, PieceType.Rook.diceValue))
            val next        = state.makeMove(move).withDicePool(afterCastle)
            val subPaths    = generateAllPaths(next)
            if subPaths.isEmpty || subPaths == List(Nil) then branches += List(move)
            else for p <- subPaths if p.nonEmpty do branches += (move :: p)
        else
          val afterMove = state.dicePool.diff(List(moverType.diceValue))
          val next      = state.makeMove(move).withDicePool(afterMove)
          val subPaths  = generateAllPaths(next)
          if subPaths.isEmpty || subPaths == List(Nil) then branches += List(move)
          else for p <- subPaths if p.nonEmpty do branches += (move :: p)

      val res = branches.result()
      if res.isEmpty then List(Nil) else res
