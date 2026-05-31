package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite

class PrudentSearchSuite extends FunSuite:

  test("PrudentSearch should detect and play a terminal king capture") {
    // White queen on a1, White king on e1. Black king on e5. White rolls queen (5).
    // Queen a1→e5 captures the black king (diagonal a1-h8).
    val fen   = "8/8/8/4k3/8/8/8/Q3K3 w - - 0 1"
    val state = FenParser
      .parse(fen)
      .fold(
        err => fail(s"Failed to parse FEN: $err"),
        state => state
      )
      .withDicePool(List(5))

    val bestMoveOpt = PrudentSearch.findBestMove(state)
    assert(bestMoveOpt.isDefined)

    val bestMove = bestMoveOpt.get
    assertEquals(bestMove.moves.size, 1)
    assertEquals(bestMove.score, SearchScoring.TerminalWinScore)
    assertEquals(bestMove.moves.head.toSquare.toNotation, "e5")
  }

  test("PrudentSearch returns a move when no captures are available") {
    // White king on e1, White pawn on d2. Black king on e8.
    // White rolls pawn (1). Simple pawn advance is the only option.
    val fen   = "4k3/8/8/8/8/8/3P4/4K3 w - - 0 1"
    val state = FenParser
      .parse(fen)
      .fold(
        err => fail(s"Failed to parse FEN: $err"),
        state => state
      )
      .withDicePool(List(1))

    val bestMoveOpt = PrudentSearch.findBestMove(state)
    assert(bestMoveOpt.isDefined)

    val moves = bestMoveOpt.get.moves
    assertEquals(moves.size, 1)
    assertEquals(moves.head.fromSquare.toNotation, "d2")
  }

  test("PrudentSearch evalWithCaptureProbability penalises positions with exposed king") {
    // Directly test that the eval function gives a lower score to a position
    // where the king is exposed vs. one where it's safe.
    import KingCaptureProbability.*

    // Exposed: White king on e1, Black rook on e8 (direct line)
    val fenExposed   = "4r3/8/8/8/8/8/8/4K3 b - - 0 1"
    val stateExposed = FenParser
      .parse(fenExposed)
      .fold(
        err => fail(s"Failed to parse FEN: $err"),
        state => state
      )
      .withDicePool(Nil)
      .endTurn()

    // Safe: White king on e1, White pawn on e2 blocks, Black rook on e8
    val fenSafe   = "4r3/8/8/8/8/8/4P3/4K3 b - - 0 1"
    val stateSafe = FenParser
      .parse(fenSafe)
      .fold(
        err => fail(s"Failed to parse FEN: $err"),
        state => state
      )
      .withDicePool(Nil)
      .endTurn()

    val color       = Color.White
    val exposedProb = kingCaptureProbability(stateExposed, color)
    val safeProb    = kingCaptureProbability(stateSafe, color)

    assert(exposedProb > safeProb, s"Exposed P=$exposedProb should be > Safe P=$safeProb")
    assert(exposedProb > 0.4, s"Exposed king should have high capture probability: $exposedProb")
    assert(safeProb > 0.0, s"A blocked rook can still be freed with pawn+rook dice: $safeProb")
  }
