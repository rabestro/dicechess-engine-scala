package dicechess.engine.api

import scala.scalajs.js
import munit.FunSuite

class JsApiSpec extends FunSuite:

  test("applyMove: simple pawn push updates FEN correctly") {
    val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val expected   = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    val result     = JsApi.applyMove(initialFen, "e2", "e4", js.undefined)
    assertEquals(result.toOption, Some(expected))
  }

  test("applyMove: castling rights are lost when king moves") {
    val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val expected   = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/R4K1R b kq - 1 1"
    val result     = JsApi.applyMove(initialFen, "e1", "f1", js.undefined)
    assertEquals(result.toOption, Some(expected))
  }

  test("applyMove: en passant capture removes pawn correctly") {
    val initialFen = "rnbqkbnr/p1pppppp/8/8/1pP5/8/PP1PPPPP/RNBQKBNR b KQkq c3 0 2"
    val expected   = "rnbqkbnr/p1pppppp/8/8/8/2p5/PP1PPPPP/RNBQKBNR w KQkq - 0 3"
    val result     = JsApi.applyMove(initialFen, "b4", "c3", js.undefined)
    assertEquals(result.toOption, Some(expected))
  }

  test("applyMove: pawn promotion to queen") {
    val initialFen = "rnbq1bnr/ppppPppp/8/8/8/8/PPP1PPPP/RNBQKBNR w KQ - 0 1"
    val expected   = "rnbqQbnr/pppp1ppp/8/8/8/8/PPP1PPPP/RNBQKBNR b KQ - 0 1"
    val result     = JsApi.applyMove(initialFen, "e7", "e8", "q")
    assertEquals(result.toOption, Some(expected))
  }

  test("applyMove: returns undefined for pseudo-illegal move") {
    val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val result     = JsApi.applyMove(initialFen, "e2", "e5", js.undefined)
    assertEquals(result.toOption, None)
  }

  test("getLegalUciMoves: pawn promotion generates UCI moves with promotion pieces") {
    // White pawn on e7, can promote to e8. Dice: [1] (Pawn)
    val fen  = "k7/4P3/8/8/8/8/8/4K3 w - - 0 1"
    val dice = js.Array(1)

    val moves = JsApi.getLegalUciMoves(fen, dice).toList

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

  test("getLegalUciMoves: handles null parameters gracefully by returning empty array") {
    val fen  = "k7/4P3/8/8/8/8/8/4K3 w - - 0 1"
    val dice = js.Array(1)
    assertEquals(JsApi.getLegalUciMoves(null, dice).length, 0)
    assertEquals(JsApi.getLegalUciMoves(fen, null).length, 0)
  }

  test("getLegalUciMoves: handles empty dice array by returning empty array") {
    val fen = "k7/4P3/8/8/8/8/8/4K3 w - - 0 1"
    assertEquals(JsApi.getLegalUciMoves(fen, js.Array()).length, 0)
  }

  test("getLegalUciMoves: handles invalid FEN by returning empty array") {
    assertEquals(JsApi.getLegalUciMoves("invalid-fen", js.Array(1)).length, 0)
  }

  test("getPieceFromDice: returns null for invalid dice values") {
    assertEquals(JsApi.getPieceFromDice(0), null)
    assertEquals(JsApi.getPieceFromDice(7), null)
  }

  test("getBestMove: handles null parameters gracefully by returning zero structure") {
    val fen  = "k7/4P3/8/8/8/8/8/4K3 w - - 0 1"
    val dice = js.Array(1)

    val res1 = JsApi.getBestMove(null, dice, js.undefined)
    assertEquals(res1.moves.length.asInstanceOf[Int], 0)
    assertEquals(res1.score.asInstanceOf[Int], 0)

    val res2 = JsApi.getBestMove(fen, null, js.undefined)
    assertEquals(res2.moves.length.asInstanceOf[Int], 0)
    assertEquals(res2.score.asInstanceOf[Int], 0)
  }

  test("getBestMove: handles invalid FEN gracefully by returning zero structure") {
    val res = JsApi.getBestMove("invalid-fen", js.Array(1), js.undefined)
    assertEquals(res.moves.length.asInstanceOf[Int], 0)
    assertEquals(res.score.asInstanceOf[Int], 0)
  }
