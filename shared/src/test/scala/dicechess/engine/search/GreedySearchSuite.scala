package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite

class GreedySearchSuite extends FunSuite:

  test("GreedySearch should prefer taking a queen over an empty square") {
    val fen   = "8/8/8/2n1q3/3P4/8/8/8 w - - 0 1"
    val state = FenParser
      .parse(fen)
      .fold(
        err => fail(s"Failed to parse FEN: $err"),
        state => state
      )
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
    val fen   = "8/8/7q/8/8/8/3P4/2B5 w - - 0 1"
    val state = FenParser
      .parse(fen)
      .fold(
        err => fail(s"Failed to parse FEN: $err"),
        state => state
      )
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

  test("GreedySearch should maximize score with 3 micro-moves") {
    // Black queen on h8. White rook on a1. White pawn on a2 blocks the rook. White knight on b1.
    // White rolls: pawn (1), knight (2), rook (4).
    // White must move pawn to unblock rook, move knight somewhere, then rook a1-a8-h8? No, rook a1-a8 is blocked?
    // Let's make a simple one:
    // White king on e1. Black queen on e4. White pawn on d2. White bishop on c1.
    // White rolls: pawn(1), bishop(3), king(6).
    // Move pawn d2-d3, then bishop c1-d2, then king e1-d1. (Not maximizing anything).
    // Let's do: black queen on h8. White bishop on a1. White pawn on b2 blocks. White pawn on c2 blocks.
    // Actually, simply: white pawn on a2, white pawn on b2, white rook on a1. Black queen on a8.
    // White rolls: pawn(1), pawn(1), rook(4).
    // White must move a2-a3, b2-b3 (or whatever), then rook takes queen.
    // Wait, pawn can move twice if we have two pawns rolled!
    val fen   = "q7/8/8/8/8/8/PP6/R7 w - - 0 1"
    val state = FenParser
      .parse(fen)
      .fold(
        err => fail(s"Failed to parse FEN: $err"),
        state => state
      )
    val bestMoveOpt = GreedySearch.findBestMove(state, List(1, 1, 4))

    assert(bestMoveOpt.isDefined)
    val bestMove = bestMoveOpt.get
    assertEquals(bestMove.moves.size, 3)
    val moves = bestMove.moves
    // One of the moves must be rook capturing on a8
    assert(moves.exists(_.toSquare.toNotation == "a8"))
  }

  test("GreedySearch should prefer terminal king capture over a multi-capture material line") {
    // White can either win immediately with Ra1xa8, or spend three rook moves taking a queen and two rooks.
    val fen   = "kr5r/8/8/8/8/8/8/R6q w - - 0 1"
    val state = FenParser
      .parse(fen)
      .fold(
        err => fail(s"Failed to parse FEN: $err"),
        state => state
      )
    val bestMoveOpt = GreedySearch.findBestMove(state, List(4, 4, 4))

    assert(bestMoveOpt.isDefined)
    val bestMove = bestMoveOpt.get
    assertEquals(bestMove.score, SearchScoring.TerminalWinScore)
    assertEquals(bestMove.moves.size, 1)
    assertEquals(bestMove.moves.head.fromSquare.toNotation, "a1")
    assertEquals(bestMove.moves.head.toSquare.toNotation, "a8")
  }

  test("SearchScoring should score king capture as an explicit terminal win") {
    val fen   = "k7/8/8/8/8/8/8/R7 w - - 0 1"
    val state = FenParser
      .parse(fen)
      .fold(
        err => fail(s"Failed to parse FEN: $err"),
        state => state
      )
    val kingCapture = Move(Square('a', 1), Square('a', 8), Move.Capture)

    val scored = SearchScoring.scorePath(state, List(kingCapture))

    assertEquals(scored.score, SearchScoring.TerminalWinScore)
  }

  test("GreedySearch should prefer the shortest terminal king capture line") {
    // Ra1xa8 wins in one move; Rh1-h8-a8 also wins, but spends an extra die.
    val fen   = "k7/8/8/8/8/8/8/R6R w - - 0 1"
    val state = FenParser
      .parse(fen)
      .fold(
        err => fail(s"Failed to parse FEN: $err"),
        state => state
      )
    val bestMoveOpt = GreedySearch.findBestMove(state, List(4, 4))

    assert(bestMoveOpt.isDefined)
    val bestMove = bestMoveOpt.get
    assertEquals(bestMove.score, SearchScoring.TerminalWinScore)
    assertEquals(bestMove.moves.size, 1)
    assertEquals(bestMove.moves.head.fromSquare.toNotation, "a1")
    assertEquals(bestMove.moves.head.toSquare.toNotation, "a8")
  }

  test("GreedySearch should return None when no legal moves exist") {
    // White king trapped, rolls only pawns, but has no pawns.
    val fen   = "8/8/8/8/8/8/8/k6K w - - 0 1"
    val state = FenParser
      .parse(fen)
      .fold(
        err => fail(s"Failed to parse FEN: $err"),
        state => state
      )
    // Roll pawns
    val bestMoveOpt = GreedySearch.findBestMove(state, List(1, 1))
    assert(bestMoveOpt.isEmpty)
  }
