package dicechess.engine.search

import dicechess.engine.domain.*
import dicechess.engine.movegen.MoveGenerator

/** Exhaustive turn-path generator for Dice Chess.
  *
  * [[TurnGenerator]] performs a depth-first search over all micro-move sequences achievable with the given dice rolls,
  * and filters the result to only the *legal* paths under the **Maximum Micro-moves Rule**:
  *
  *   - A path is legal if it ends with a King capture (win condition), *or*
  *   - it consumes the maximum achievable number of dice (castling spends two dice in a single move).
  *
  * This object is used by [[SearchAlgorithm]] implementations to obtain the candidate set before scoring.
  *
  * @note
  *   Active color is kept constant throughout the turn — it is *not* toggled between micro-moves. `makeMove` preserves
  *   the side, so we can chain micro-moves directly. The turn is only flipped explicitly at the end by the caller.
  */
object TurnGenerator:

  /** Generates all legal full-turn paths (sequences of 1 to 3 moves) for the given state.
    *
    * A path is a `List[Move]` of 1–3 micro-moves. An empty list means no legal move exists (the player passes). The
    * filtering guarantees that the returned paths either end with a King capture or consume the maximum achievable
    * number of dice (castling counts as two).
    *
    * @param state
    *   the current [[GameState]]; `state.activeColor` indicates who is moving
    * @return
    *   a (possibly empty) list of legal full-turn paths; each path contains 1–3 moves
    */
  def generateAllLegalTurnPaths(state: GameState): List[List[Move]] =
    val builder = List.newBuilder[List[Move]]
    forEachLegalTurnPath(state) { (moves, len) =>
      builder += moves.take(len).toList
    }
    builder.result()

  /** Traverses all legal turn paths (sequences of 1 to 3 moves) using a visitor/callback pattern.
    *
    * The callback `f` receives the path as an array of moves and the length of the path.
    *
    * @note
    *   To avoid garbage collection pressure, the array passed to the callback is mutable and reused across calls. If
    *   the callback needs to persist the path, it must copy the elements (e.g., using `moves.take(len).toList`).
    *
    * @param state
    *   the current [[GameState]]
    * @param f
    *   the callback function invoked for each legal path
    */
  def forEachLegalTurnPath(state: GameState)(f: (Array[Move], Int) => Unit): Unit =
    val moves = MoveGenerator.generateMoves(state)
    if moves.nonEmpty then
      val ctx         = new TurnGenContext()
      val currentPath = new Array[Move](3)
      generatePathsSinglePass(state, currentPath, 0, 0, ctx, moves)

      if ctx.kingCaptureCount > 0 || ctx.normalCount > 0 then
        val outPath = new Array[Move](3)
        var i       = 0
        while i < ctx.kingCaptureCount do
          val p   = ctx.kingCaptures(i)
          val len = ((p >>> 48) & 0xffL).toInt
          outPath(0) = (p & 0xffffL).toInt.asInstanceOf[Move]
          outPath(1) = ((p >>> 16) & 0xffffL).toInt.asInstanceOf[Move]
          outPath(2) = ((p >>> 32) & 0xffffL).toInt.asInstanceOf[Move]
          f(outPath, len)
          i += 1

        i = 0
        val maxDice = ctx.maxDice
        while i < ctx.normalCount do
          val p    = ctx.normalPaths(i)
          val dice = ((p >>> 56) & 0xffL).toInt
          if dice == maxDice then
            val len = ((p >>> 48) & 0xffL).toInt
            outPath(0) = (p & 0xffffL).toInt.asInstanceOf[Move]
            outPath(1) = ((p >>> 16) & 0xffffL).toInt.asInstanceOf[Move]
            outPath(2) = ((p >>> 32) & 0xffffL).toInt.asInstanceOf[Move]
            f(outPath, len)
          i += 1

  /** Returns `true` when `move` captures the opponent's King from `state`. */
  private def isKingCapture(state: GameState, move: Move): Boolean =
    state.mailbox
      .get(move.toSquare)
      .exists(p => p.pieceType == PieceType.King && p.color != state.activeColor)

  private def packPath(path: Array[Move], len: Int, dice: Int): Long =
    val m0 = if len > 0 then path(0).toInt else Move.empty.toInt
    val m1 = if len > 1 then path(1).toInt else Move.empty.toInt
    val m2 = if len > 2 then path(2).toInt else Move.empty.toInt
    (m0.toLong & 0xffffL) |
      ((m1.toLong & 0xffffL) << 16) |
      ((m2.toLong & 0xffffL) << 32) |
      ((len.toLong & 0xffL) << 48) |
      ((dice.toLong & 0xffL) << 56)

  private class TurnGenContext:
    var normalPaths      = new Array[Long](128)
    var normalCount      = 0
    var kingCaptures     = new Array[Long](32)
    var kingCaptureCount = 0
    var maxDice          = 0

    def addNormal(p: Long): Unit =
      val dice = ((p >>> 56) & 0xffL).toInt
      if dice > maxDice then maxDice = dice
      if normalCount >= normalPaths.length then
        val next = new Array[Long](normalPaths.length * 2)
        System.arraycopy(normalPaths, 0, next, 0, normalPaths.length)
        normalPaths = next
      normalPaths(normalCount) = p
      normalCount += 1

    def addKingCapture(p: Long): Unit =
      if kingCaptureCount >= kingCaptures.length then
        val next = new Array[Long](kingCaptures.length * 2)
        System.arraycopy(kingCaptures, 0, next, 0, kingCaptures.length)
        kingCaptures = next
      kingCaptures(kingCaptureCount) = p
      kingCaptureCount += 1

  private def generatePathsSinglePass(
      state: GameState,
      currentPath: Array[Move],
      depth: Int,
      diceConsumedSoFar: Int,
      ctx: TurnGenContext,
      moves: List[Move]
  ): Unit =
    val pool = state.dicePool
    var curr = moves
    while curr.nonEmpty do
      val move = curr.head
      currentPath(depth) = move
      val moverType = state.mailbox(move.fromSquare).pieceType
      if isKingCapture(state, move) then
        val consumed = diceConsumedSoFar + (if move.isCastling then 2 else 1)
        val packed   = packPath(currentPath, depth + 1, consumed)
        ctx.addKingCapture(packed)
      else if move.isCastling then
        if pool.contains(PieceType.King.diceValue) && pool.contains(PieceType.Rook.diceValue) then
          val afterCastle = pool.diff(List(PieceType.King.diceValue, PieceType.Rook.diceValue))
          val next        = state.makeMove(move).withDicePool(afterCastle)
          val subMoves    = MoveGenerator.generateMoves(next)
          if subMoves.isEmpty then
            val consumed = diceConsumedSoFar + 2
            val packed   = packPath(currentPath, depth + 1, consumed)
            ctx.addNormal(packed)
          else
            val normalBefore = ctx.normalCount
            val kingBefore   = ctx.kingCaptureCount
            generatePathsSinglePass(next, currentPath, depth + 1, diceConsumedSoFar + 2, ctx, subMoves)
            if ctx.normalCount == normalBefore && ctx.kingCaptureCount == kingBefore then
              val consumed = diceConsumedSoFar + 2
              val packed   = packPath(currentPath, depth + 1, consumed)
              ctx.addNormal(packed)
      else
        val afterMove = pool.diff(List(moverType.diceValue))
        if afterMove.size < pool.size then
          val next     = state.makeMove(move).withDicePool(afterMove)
          val subMoves = MoveGenerator.generateMoves(next)
          if subMoves.isEmpty then
            val consumed = diceConsumedSoFar + 1
            val packed   = packPath(currentPath, depth + 1, consumed)
            ctx.addNormal(packed)
          else
            val normalBefore = ctx.normalCount
            val kingBefore   = ctx.kingCaptureCount
            generatePathsSinglePass(next, currentPath, depth + 1, diceConsumedSoFar + 1, ctx, subMoves)
            if ctx.normalCount == normalBefore && ctx.kingCaptureCount == kingBefore then
              val consumed = diceConsumedSoFar + 1
              val packed   = packPath(currentPath, depth + 1, consumed)
              ctx.addNormal(packed)
        else
          sys.error(
            s"CRITICAL: Dice pool $pool does not decrease! moverType=$moverType, moverType.diceValue=${moverType.diceValue}, move=${move.fromSquare.toNotation}${move.toSquare.toNotation}, state.activeColor=${state.activeColor}, state.mailbox(move.fromSquare)=${state.mailbox(move.fromSquare)}"
          )
      curr = curr.tail
