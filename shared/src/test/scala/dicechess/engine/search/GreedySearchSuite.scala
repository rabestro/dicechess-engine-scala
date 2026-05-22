package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite

class GreedySearchSuite extends FunSuite:

  test("GreedySearch should prefer taking a queen over an empty square") {
    val fen         = "8/8/8/2n1q3/3P4/8/8/8 w - - 0 1"
    val state       = FenParser.parse(fen).toOption.get
    val bestMoveOpt = GreedySearch.findBestMove(state, List(1))

    assert(bestMoveOpt.isDefined)
    val bestMove = bestMoveOpt.get
    assertEquals(bestMove.moves.size, 1)
    val move = bestMove.moves.head
    assertEquals(move.toSquare.toNotation, "e5") // Takes the queen
  }

  test("GreedySearch should evaluate sequence of 2 moves to maximize score") {
    // Black queen on h6. White bishop on c1. White pawn on d2 blocks the bishop.
    // White rolls pawn (1) and bishop (3).
    // White must move pawn to unblock the bishop, then bishop c1-h6 to take queen.
    val fen         = "8/8/7q/8/8/8/3P4/2B5 w - - 0 1"
    val state       = FenParser.parse(fen).toOption.get
    val bestMoveOpt = GreedySearch.findBestMove(state, List(1, 3))

    assert(bestMoveOpt.isDefined)
    val bestMove = bestMoveOpt.get
    assertEquals(bestMove.moves.size, 2)
    val move1 = bestMove.moves(0)
    val move2 = bestMove.moves(1)

    assertEquals(move1.fromSquare.toNotation, "d2")
    assertEquals(move2.fromSquare.toNotation, "c1")
    assertEquals(move2.toSquare.toNotation, "h6")
  }
