package dicechess.engine.search

import dicechess.engine.domain.{Color, GameState, Bitboard}

/** Evaluator for Dice Chess positions combining material balance and king safety.
  *
  * Assigns piece values (in centipawns) and applies a significant penalty if the side's king is exposed to immediate
  * capture. This helps greedy strategies avoid "material-greedy" blunders that lead to instant loss.
  *
  * ## Piece values
  *
  * | Piece  | Value |
  * |:-------|------:|
  * | Pawn   |   100 |
  * | Knight |   300 |
  * | Bishop |   300 |
  * | Rook   |   500 |
  * | Queen  |   900 |
  * | King   | 10000 |
  */
object Evaluator:

  /** Penalty applied when a player's king is attacked by an enemy piece, leaving it exposed to immediate capture. Set
    * to 2000 centipawns (worth more than a Queen) to heavily discourage material gains that expose the king.
    */
  val KingExposurePenalty: Int = 2000

  /** Evaluates the overall position for the given `color`, including material and king safety heuristics.
    *
    * @param state
    *   the current [[GameState]]
    * @param color
    *   the perspective from which to evaluate
    * @return
    *   signed centipawn score: positive means `color` is ahead, negative means behind
    */
  def evaluate(state: GameState, color: Color): Int =
    evaluateMaterial(state, color) + evaluateKingSafety(state, color)

  /** Evaluates king safety for the given `color`.
    *
    * Returns a negative penalty if the `color`'s king is attacked by an opponent's piece, or 0 if safe.
    *
    * @param state
    *   the current [[GameState]]
    * @param color
    *   the side whose safety is evaluated
    * @return
    *   penalty value (negative or 0)
    */
  def evaluateKingSafety(state: GameState, color: Color): Int =
    import dicechess.engine.movegen.MoveGenerator
    import dicechess.engine.domain.Square
    val myPieces = if color.isWhite then state.whitePieces else state.blackPieces
    val myKings  = state.kings & myPieces
    val oppColor = color.opponent

    var isAttacked = false
    var p          = myKings.value
    while (p != 0L && !isAttacked) {
      val sqIdx = java.lang.Long.numberOfTrailingZeros(p)
      val sq    = Square.fromIndex(sqIdx)
      if !MoveGenerator.isSquareAttacked(state, sq, oppColor).isEmpty then isAttacked = true
      p &= (p - 1L)
    }
    if isAttacked then -KingExposurePenalty else 0

  /** Evaluates the material balance for the given `color`.
    *
    * A positive return value indicates that `color` has more material than the opponent; a negative value indicates a
    * material deficit.
    *
    * @param state
    *   the current [[GameState]]
    * @param color
    *   the perspective from which to evaluate (typically `state.activeColor`)
    * @return
    *   signed centipawn score: positive means `color` is ahead, negative means behind
    */
  def evaluateMaterial(state: GameState, color: Color): Int =
    val myPieces  = if color.isWhite then state.whitePieces else state.blackPieces
    val oppPieces = if color.isWhite then state.blackPieces else state.whitePieces

    scoreBitboard(state, myPieces) - scoreBitboard(state, oppPieces)

  /** Computes the total centipawn value of all pieces in `bb`.
    *
    * Intersects `bb` with each piece-type bitboard to count pieces by type, then multiplies each count by the
    * corresponding fixed piece value.
    *
    * @param state
    *   the current [[GameState]] providing piece-type bitboards
    * @param bb
    *   the subset of squares to score (typically one side's pieces)
    * @return
    *   the total centipawn value of pieces on squares in `bb`
    */
  private def scoreBitboard(state: GameState, bb: Bitboard): Int =
    var s = 0
    s += (bb & state.pawns).count * 100
    s += (bb & state.knights).count * 300
    s += (bb & state.bishops).count * 300
    s += (bb & state.rooks).count * 500
    s += (bb & state.queens).count * 900
    s += (bb & state.kings).count * 10000
    s

  /** Evaluates the position with aggressive (king hunt) heuristics for the given `color`.
    *
    * Combines standard evaluation (material + king safety) with:
    *   - **Pawn Storm Heuristics:** bonus for advanced friendly pawns.
    *   - **King Proximity Heuristics:** bonus for friendly pieces close to the enemy King.
    *   - **King Ring Pressure Heuristics:** bonus for friendly pieces attacking squares surrounding the enemy King.
    *
    * @param state
    *   current [[GameState]]
    * @param color
    *   player color whose perspective to evaluate
    * @return
    *   centipawn score favoring active attacks on the enemy King
    */
  def evaluateAggressive(state: GameState, color: Color): Int =
    import dicechess.engine.domain.Square
    import dicechess.engine.movegen.MoveGenerator

    val standardScore = evaluate(state, color)
    val oppPieces     = if color.isWhite then state.blackPieces else state.whitePieces
    val oppKings      = state.kings & oppPieces

    if oppKings.isEmpty then standardScore // No enemy king to attack (forced win or invalid FEN)
    else
      val ekIdx  = java.lang.Long.numberOfTrailingZeros(oppKings.value)
      val ekSq   = Square.fromIndex(ekIdx)
      val ekRank = ekSq.rank
      val ekFile = ekSq.file.toInt

      val myPieces      = if color.isWhite then state.whitePieces else state.blackPieces
      val friendlyPawns = state.pawns & myPieces

      // 1. Pawn Storm Heuristic
      var pawnStormBonus = 0
      var pw             = friendlyPawns.value
      while (pw != 0L) {
        val sqIdx       = java.lang.Long.numberOfTrailingZeros(pw)
        val sq          = Square.fromIndex(sqIdx)
        val advancement = if color.isWhite then sq.rank - 2 else 7 - sq.rank
        pawnStormBonus += advancement * 15
        pw &= (pw - 1L)
      }

      // 2. King Proximity Heuristic (Chebyshev distance of Knights, Bishops, Rooks, Queens)
      val attackers      = (state.knights | state.bishops | state.rooks | state.queens) & myPieces
      var proximityBonus = 0
      var p              = attackers.value
      while (p != 0L) {
        val sqIdx  = java.lang.Long.numberOfTrailingZeros(p)
        val sq     = Square.fromIndex(sqIdx)
        val dist   = Math.max(Math.abs(sq.rank - ekRank), Math.abs(sq.file.toInt - ekFile))
        val weight = if state.queens.contains(sq) then 40
        else if state.rooks.contains(sq) then 25
        else 15
        proximityBonus += (8 - dist) * weight
        p &= (p - 1L)
      }

      // 3. King Ring Pressure Heuristic
      var kingRingPressure = 0
      for {
        r <- (ekRank - 1) to (ekRank + 1)
        f <- (ekFile - 1) to (ekFile + 1)
        if r >= 1 && r <= 8 && f >= 'a'.toInt && f <= 'h'.toInt
        if !(r == ekRank && f == ekFile)
      } {
        val targetSq          = Square(f.toChar, r)
        val friendlyAttackers = MoveGenerator.isSquareAttacked(state, targetSq, color)
        if !friendlyAttackers.isEmpty then kingRingPressure += friendlyAttackers.count * 25
      }

      standardScore + pawnStormBonus + proximityBonus + kingRingPressure
