package dicechess.engine.search

import dicechess.engine.domain.*
import munit.ScalaCheckSuite
import org.scalacheck.Prop.*

class TurnGeneratorPropertySpec extends ScalaCheckSuite:

  private val domainProps = new dicechess.engine.domain.PropertySpec()
  private val gameStateGen = domainProps.gameStateGen.filter { state =>
    // Pawns cannot be on rank 1 (index 0-7) or rank 8 (index 56-63)
    val pawnsVal = state.pawns.value
    var temp = pawnsVal
    var ok = true
    while temp != 0 && ok do
      val idx = java.lang.Long.numberOfTrailingZeros(temp)
      val rank = idx / 8
      if rank == 0 || rank == 7 then ok = false
      temp &= temp - 1
    ok
  }

  // The old list-based implementation to verify against
  private def generateAllLegalTurnPathsOld(state: GameState): List[List[Move]] =
    val allPaths = generateAllPathsOld(state).filter(_.nonEmpty)
    if allPaths.isEmpty then Nil
    else
      val maxDice = allPaths.map(diceConsumedOld).maxOption.getOrElse(0)
      allPaths.filter(p => isKingCapturePathOld(state, p) || diceConsumedOld(p) == maxDice)

  private def diceConsumedOld(path: List[Move]): Int =
    path.foldLeft(0)((acc, move) => acc + (if move.isCastling then 2 else 1))

  private def isKingCaptureOld(state: GameState, move: Move): Boolean =
    state.mailbox
      .get(move.toSquare)
      .exists(p => p.pieceType == PieceType.King && p.color != state.activeColor)

  private def isKingCapturePathOld(initialState: GameState, path: List[Move]): Boolean =
    if path.isEmpty then false
    else
      val stateBeforeLast = path.init.foldLeft(initialState) { (s, m) =>
        s.makeMove(m)
      }
      isKingCaptureOld(stateBeforeLast, path.last)

  private def generateAllPathsOld(state: GameState): List[List[Move]] =
    if state.dicePool.isEmpty then List(Nil)
    else
      val branches = List.newBuilder[List[Move]]

      for move <- dicechess.engine.movegen.MoveGenerator.generateMoves(state) do
        val moverType = state.mailbox(move.fromSquare).pieceType
        if isKingCaptureOld(state, move) then branches += List(move)
        else if move.isCastling then
          if state.dicePool.contains(PieceType.King.diceValue) && state.dicePool.contains(PieceType.Rook.diceValue) then
            val afterCastle = state.dicePool.diff(List(PieceType.King.diceValue, PieceType.Rook.diceValue))
            val next        = state.makeMove(move).withDicePool(afterCastle)
            val subPaths    = generateAllPathsOld(next)
            if subPaths.isEmpty || subPaths == List(Nil) then branches += List(move)
            else for p <- subPaths if p.nonEmpty do branches += (move :: p)
        else
          val afterMove = state.dicePool.diff(List(moverType.diceValue))
          if afterMove.size < state.dicePool.size then
            val next     = state.makeMove(move).withDicePool(afterMove)
            val subPaths = generateAllPathsOld(next)
            if subPaths.isEmpty || subPaths == List(Nil) then branches += List(move)
            else for p <- subPaths if p.nonEmpty do branches += (move :: p)

      val res = branches.result()
      if res.isEmpty then List(Nil) else res

  property("new TurnGenerator visitor-based paths are identical to the old implementation") {
    forAll(gameStateGen) { (state: GameState) =>
      val oldPaths = generateAllLegalTurnPathsOld(state).map(_.map(_.toString).mkString(",")).sorted
      val newPaths = TurnGenerator.generateAllLegalTurnPaths(state).map(_.map(_.toString).mkString(",")).sorted
      assertEquals(newPaths, oldPaths)
    }
  }
