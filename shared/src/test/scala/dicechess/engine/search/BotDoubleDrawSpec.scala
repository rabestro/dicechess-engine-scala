package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite

class BotDoubleDrawSpec extends FunSuite:

  private def parseState(fen: String): GameState =
    FenParser.parse(fen).getOrElse(fail(s"Failed to parse FEN: $fen"))

  test("Cautious Greedy (GreedySearchV2) doubling and draw decisions") {
    // 1. Winning position for White: White is up a Queen and multiple pieces
    val winFen   = "k7/8/8/8/8/8/PPPPPPPP/QNBK4 w - - 0 1"
    val winState = parseState(winFen)

    // Win probability should be very high (> 70%)
    assert(GreedySearchV2.shouldOfferDouble(winState, 1))
    assert(GreedySearchV2.shouldAcceptDouble(winState, 2))
    assert(!GreedySearchV2.shouldOfferDraw(winState))
    assert(!GreedySearchV2.shouldAcceptDraw(winState))

    // 2. Losing position for White: White has only King, Black has Queen and Rook
    val loseFen   = "k7/q7/r7/8/8/8/8/4K3 w - - 0 1"
    val loseState = parseState(loseFen)

    // Win probability should be very low (< 25%)
    assert(!GreedySearchV2.shouldOfferDouble(loseState, 1))
    assert(!GreedySearchV2.shouldAcceptDouble(loseState, 2))
    assert(!GreedySearchV2.shouldOfferDraw(loseState))
    assert(GreedySearchV2.shouldAcceptDraw(loseState)) // Cautious bot accepts draw when losing

    // 3. Balanced equal position
    val equalFen   = "k7/8/8/8/8/8/8/K7 w - - 0 1"
    val equalState = parseState(equalFen)

    assert(!GreedySearchV2.shouldOfferDouble(equalState, 1))
    assert(
      GreedySearchV2.shouldAcceptDouble(equalState, 2)
    ) // Equal position is ~50% P(win), which is > 35% Take threshold

    // Draw offer in early game (move 1) should be false
    assert(!GreedySearchV2.shouldOfferDraw(equalState))

    // Draw offer in late game (move 31) should be true
    val lateEqualState = equalState.copy(fullMoveNumber = 31)
    assert(GreedySearchV2.shouldOfferDraw(lateEqualState))
  }

  test("AggressiveSearch doubling and draw decisions") {
    // 1. Winning position for White
    val winFen   = "k7/8/8/8/8/8/PPPPPPPP/QNBK4 w - - 0 1"
    val winState = parseState(winFen)

    assert(AggressiveSearch.shouldOfferDouble(winState, 1))
    assert(AggressiveSearch.shouldAcceptDouble(winState, 2))
    assert(!AggressiveSearch.shouldOfferDraw(winState))
    assert(!AggressiveSearch.shouldAcceptDraw(winState))

    // 2. Slightly advantageous position for White (e.g. up a Pawn)
    // Eval is positive, but not completely overwhelming. Aggressive bot should double early (> 55% win probability).
    val slightAdvantageFen = "k7/8/8/8/8/8/1P6/K7 w - - 0 1" // White has Pawn on B2, Black has no pawns
    val advantageState     = parseState(slightAdvantageFen)

    // Aggressive bot offers double with smaller threshold
    assert(AggressiveSearch.shouldOfferDouble(advantageState, 1))

    // 3. Lost position for White
    val loseFen   = "k7/q7/r7/8/8/8/8/4K3 w - - 0 1"
    val loseState = parseState(loseFen)

    assert(!AggressiveSearch.shouldOfferDouble(loseState, 1))
    assert(!AggressiveSearch.shouldAcceptDouble(loseState, 2))
    assert(!AggressiveSearch.shouldOfferDraw(loseState))
    assert(!AggressiveSearch.shouldAcceptDraw(loseState)) // Aggressive bot never accepts draw
  }
