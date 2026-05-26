package dicechess.engine.search

import dicechess.engine.domain.*
import dicechess.engine.movegen.MoveGenerator

object TurnGenerator:

  /** Generates all legal full-turn paths (sequences of 1 to 3 moves) for the given state and dice.
    *
    * A path is considered a legal full turn if:
    *   1. It ends with a King capture.
    *   2. It achieves the maximum possible length achievable with the given dice.
    */
  def generateAllLegalTurnPaths(state: GameState, dice: List[Int]): List[List[Move]] =
    val allPaths = generateAllPaths(state, dice).filter(_.nonEmpty)
    if allPaths.isEmpty then Nil
    else
      val maxLen = allPaths.map(_.size).maxOption.getOrElse(0)
      allPaths.filter(p => isKingCapturePath(state, p) || p.size == maxLen)

  private def isKingCapture(state: GameState, move: Move): Boolean =
    state.mailbox
      .get(move.toSquare)
      .exists(p => p.pieceType == PieceType.King && p.color != state.activeColor)

  private def isKingCapturePath(initialState: GameState, path: List[Move]): Boolean =
    if path.isEmpty then false
    else
      val stateBeforeLast = path.init.foldLeft(initialState) { (s, m) =>
        s.makeMove(m).withActiveColor(s.activeColor)
      }
      isKingCapture(stateBeforeLast, path.last)

  private def generateAllPaths(state: GameState, remainingDice: List[Int]): List[List[Move]] =
    if remainingDice.isEmpty then List(Nil)
    else
      val activeColor = state.activeColor
      val branches    = List.newBuilder[List[Move]]
      var hasAnyMove  = false

      for d <- remainingDice.distinct do
        for move <- MoveGenerator.generateMoves(state, d) do
          hasAnyMove = true
          if isKingCapture(state, move) then branches += List(move)
          else if move.isCastling then
            if remainingDice.contains(PieceType.King.diceValue) &&
              remainingDice.contains(PieceType.Rook.diceValue)
            then
              val afterCastle = remainingDice.diff(List(PieceType.King.diceValue, PieceType.Rook.diceValue))
              val next        = state.makeMove(move).withActiveColor(activeColor).withDicePool(afterCastle)
              val subPaths    = generateAllPaths(next, afterCastle)
              if subPaths.isEmpty || subPaths == List(Nil) then branches += List(move)
              else for p <- subPaths if p.nonEmpty do branches += (move :: p)
          else
            val afterMove = remainingDice.diff(List(d))
            val next      = state.makeMove(move).withActiveColor(activeColor).withDicePool(afterMove)
            val subPaths  = generateAllPaths(next, afterMove)
            if subPaths.isEmpty || subPaths == List(Nil) then branches += List(move)
            else for p <- subPaths if p.nonEmpty do branches += (move :: p)

      val res = branches.result()
      if !hasAnyMove then List(Nil) else res
