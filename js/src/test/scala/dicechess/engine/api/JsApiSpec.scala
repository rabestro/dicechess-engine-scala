package dicechess.engine.api

import scala.scalajs.js
import munit.FunSuite

class JsApiSpec extends FunSuite:

  test("getAvailableBots: returns a list of supported bots") {
    val bots = JsApi.getAvailableBots.toList
    assert(bots.nonEmpty)

    val randomBot = bots.find(_.id.asInstanceOf[String] == "random").get
    assertEquals(randomBot.name.asInstanceOf[String], "Random")
    assertEquals(randomBot.difficulty.asInstanceOf[Int], 1)

    val greedyBot = bots.find(_.id.asInstanceOf[String] == "greedy").get
    assertEquals(greedyBot.name.asInstanceOf[String], "Greedy")
    assertEquals(greedyBot.difficulty.asInstanceOf[Int], 3)
  }

  test("applyMove: simple pawn push updates FEN correctly") {
    val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val expected   = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e3 0 1"
    val result     = JsApi.applyMove(initialFen, "e2", "e4", js.undefined)
    assertEquals(result.toOption, Some(expected))
  }

  test("applyMove: castling rights are lost when king moves") {
    val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val expected   = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/R4K1R w kq - 1 1"
    val result     = JsApi.applyMove(initialFen, "e1", "f1", js.undefined)
    assertEquals(result.toOption, Some(expected))
  }

  test("applyMove: en passant capture removes pawn correctly") {
    val initialFen = "rnbqkbnr/p1pppppp/8/8/1pP5/8/PP1PPPPP/RNBQKBNR b KQkq c3 0 2"
    val expected   = "rnbqkbnr/p1pppppp/8/8/8/2p5/PP1PPPPP/RNBQKBNR b KQkq - 0 2"
    val result     = JsApi.applyMove(initialFen, "b4", "c3", js.undefined)
    assertEquals(result.toOption, Some(expected))
  }

  test("applyMove: pawn promotion to queen") {
    val initialFen = "rnbq1bnr/ppppPppp/8/8/8/8/PPP1PPPP/RNBQKBNR w KQ - 0 1"
    val expected   = "rnbqQbnr/pppp1ppp/8/8/8/8/PPP1PPPP/RNBQKBNR w KQ - 0 1"
    val result     = JsApi.applyMove(initialFen, "e7", "e8", "q")
    assertEquals(result.toOption, Some(expected))
  }

  test("applyMove: returns undefined for pseudo-illegal move") {
    val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val result     = JsApi.applyMove(initialFen, "e2", "e5", js.undefined)
    assertEquals(result.toOption, None)
  }

  test("endTurn: explicitly transitions turn, toggles color, increments full-move for Black, and clears stale EP") {
    // White's turn ends. Black had created an EP on c6 previously.
    // White also created an EP on e3.
    // When White ends turn, c6 must be cleared (White missed the chance). e3 must remain (for Black to capture).
    // The FEN also has 'PN' in the 7th field, simulating leftover dice. endTurn should clear them.
    val beforeFen = "rnbqkbnr/p1pppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6e3 0 1 PN"
    val expected  = "rnbqkbnr/p1pppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    val result    = JsApi.endTurn(beforeFen)
    assertEquals(result.toOption, Some(expected))
  }

  test("endTurn: increments full-move when transitioning from Black to White") {
    val beforeFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1"
    val expected  = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 2"
    val result    = JsApi.endTurn(beforeFen)
    assertEquals(result.toOption, Some(expected))
  }

  test("getLegalUciMoves: pawn promotion generates UCI moves with promotion pieces") {
    // White pawn on e7, can promote to e8. Dice: [1] (Pawn)
    val dfen = "k7/4P3/8/8/8/8/8/4K3 w - - 0 1 P"

    val moves = JsApi.getLegalUciMoves(dfen).toList

    // Should generate all 4 promotions for e7e8
    assert(moves.contains("e7e8q"))
    assert(moves.contains("e7e8r"))
    assert(moves.contains("e7e8b"))
    assert(moves.contains("e7e8n"))
  }

  // --- Negative and Boundary Tests ---

  test("applyMove: handles null parameters gracefully by returning undefined") {
    val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    assertEquals(JsApi.applyMove(null, "e2", "e4", js.undefined).toOption, None)
    assertEquals(JsApi.applyMove(initialFen, null, "e4", js.undefined).toOption, None)
    assertEquals(JsApi.applyMove(initialFen, "e2", null, js.undefined).toOption, None)
  }

  test("applyMove: handles malformed FEN or coordinates by returning undefined") {
    val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    assertEquals(JsApi.applyMove("invalid-fen", "e2", "e4", js.undefined).toOption, None)
    assertEquals(JsApi.applyMove(initialFen, "e9", "e4", js.undefined).toOption, None)
    assertEquals(JsApi.applyMove(initialFen, "e2", "z4", js.undefined).toOption, None)
    assertEquals(JsApi.applyMove(initialFen, "foo", "bar", js.undefined).toOption, None)
  }

  test("endTurn: handles null parameter gracefully by returning undefined") {
    assertEquals(JsApi.endTurn(null).toOption, None)
  }

  test("endTurn: handles malformed FEN gracefully by returning undefined") {
    assertEquals(JsApi.endTurn("invalid-fen").toOption, None)
  }

  test("getLegalUciMoves: handles null parameters gracefully by returning empty array") {
    assertEquals(JsApi.getLegalUciMoves(null).length, 0)
  }

  test("getLegalUciMoves: handles FEN without dice by returning empty array if no moves possible") {
    val dfen = "k7/4P3/8/8/8/8/8/4K3 w - - 0 1"
    assertEquals(JsApi.getLegalUciMoves(dfen).length, 0)
  }

  test("getLegalUciMoves: handles invalid FEN by returning empty array") {
    assertEquals(JsApi.getLegalUciMoves("invalid-fen").length, 0)
  }

  test("getPieceFromDice: returns null for invalid dice values") {
    assertEquals(JsApi.getPieceFromDice(0), null)
    assertEquals(JsApi.getPieceFromDice(7), null)
  }

  test("getBestMove: handles null parameters gracefully by returning zero structure") {
    val res1 = JsApi.getBestMove(null, js.undefined)
    assertEquals(res1.moves.length.asInstanceOf[Int], 0)
    assertEquals(res1.score.asInstanceOf[Int], 0)
  }

  test("getBestMove: handles invalid FEN gracefully by returning zero structure") {
    val res = JsApi.getBestMove("invalid-fen", js.undefined)
    assertEquals(res.moves.length.asInstanceOf[Int], 0)
    assertEquals(res.score.asInstanceOf[Int], 0)
  }
