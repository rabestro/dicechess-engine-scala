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

  test("getPromotions: returns all promotions if not constrained by dice") {
    val fen      = "3k4/5P2/2P5/8/5K2/8/8/8 w - - 0 1"
    val dice     = js.Array(1, 6, 6) // p, k, k
    val result   = JsApi.getPromotions(fen, dice, "f7", "f8")
    val expected = Set("q", "r", "b", "n")
    assertEquals(result.toSet, expected)
  }

  test("getPromotions: returns forced promotion to knight if knight die is unused") {
    val fen      = "3k4/5P2/2P5/8/5K2/8/8/8 w - - 0 1"
    val dice     = js.Array(2, 1) // n, p
    val result   = JsApi.getPromotions(fen, dice, "f7", "f8")
    val expected = Set("n")
    assertEquals(result.toSet, expected)
  }
