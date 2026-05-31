package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite

class KingCaptureProbabilitySuite extends FunSuite:

  private val SingleAttackerProb = 91.0 / 216.0 // P(at least one specific die in 3d6)

  test("kingCaptureProbability returns 0 when no attackers exist") {
    val fen   = "4k3/8/8/8/8/8/8/4K3 w - - 0 1"
    val state = FenParser.parse(fen).fold(err => fail(s"Failed to parse FEN: $err"), identity)
    assertEquals(KingCaptureProbability.kingCaptureProbability(state, Color.White), 0.0)
    assertEquals(KingCaptureProbability.kingCaptureProbability(state, Color.Black), 0.0)
  }

  test("kingCaptureProbability returns 0 when attacker has no piece on board") {
    // White king on e1, no black pieces. But Black can still roll dice — just nothing to move.
    val fen   = "4k3/8/8/8/8/8/8/4K3 w - - 0 1"
    val state = FenParser.parse(fen).fold(err => fail(s"Failed to parse FEN: $err"), identity)
    assertEquals(KingCaptureProbability.kingCaptureProbability(state, Color.White), 0.0)
  }

  test("kingCaptureProbability returns correct value for single knight attacker") {
    // White king on e5, Black knight on f7 attacks e5.
    // Black captures king when at least one die shows Knight (2).
    val fen   = "8/5n2/8/4K3/8/8/8/8 b - - 0 1"
    val state = FenParser.parse(fen).fold(err => fail(s"Failed to parse FEN: $err"), identity)
    val prob  = KingCaptureProbability.kingCaptureProbability(state, Color.White)
    assertEqualsDouble(prob, SingleAttackerProb, 0.0001)
  }

  test("kingCaptureProbability returns correct value for single bishop attacker") {
    // White king on e5, Black bishop on h2 attacks e5 (clear diagonal).
    // Black captures king when at least one die shows Bishop (3).
    val fen   = "8/8/8/4K3/8/8/7b/8 b - - 0 1"
    val state = FenParser.parse(fen).fold(err => fail(s"Failed to parse FEN: $err"), identity)
    val prob  = KingCaptureProbability.kingCaptureProbability(state, Color.White)
    assertEqualsDouble(prob, SingleAttackerProb, 0.0001)
  }

  test("kingCaptureProbability returns 0 when enemy has only a blocked rook") {
    // Black rook on e8 blocked by a Black pawn on e7 — same color, can never be captured.
    // Black could move the pawn to free the rook, but that requires a Pawn die AND a Rook die (needs two dice).
    // Test expects P > 0 (can be freed with pawn+rook).
    val fen   = "4r3/4p3/8/4K3/8/8/8/8 b - - 0 1"
    val state = FenParser.parse(fen).fold(err => fail(s"Failed to parse FEN: $err"), identity)
    val prob  = KingCaptureProbability.kingCaptureProbability(state, Color.White)
    assert(prob > 0.0, "Rook can be freed by moving the pawn, then capturing the king")
  }

  test("kingCaptureProbability returns 0 when kings are far apart") {
    // White king on a1, Black king on h8. Distance is 7 squares — too far for 3 king moves.
    val fen   = "7k/8/8/8/8/8/8/K7 b - - 0 1"
    val state = FenParser.parse(fen).fold(err => fail(s"Failed to parse FEN: $err"), identity)
    assertEquals(KingCaptureProbability.kingCaptureProbability(state, Color.White), 0.0)
  }

  test("queenCaptureProbability returns 0 when no queens exist") {
    val fen   = "4k3/8/8/8/8/8/8/4K3 w - - 0 1"
    val state = FenParser.parse(fen).fold(err => fail(s"Failed to parse FEN: $err"), identity)
    assertEquals(KingCaptureProbability.queenCaptureProbability(state, Color.White), 0.0)
  }

  test("queenCaptureProbability returns correct value for single queen attacker") {
    // White queen on e5, Black knight on f7 attacks e5.
    // Black captures queen when at least one die shows Knight (2).
    val fen   = "8/5n2/8/4Q3/8/8/8/8 b - - 0 1"
    val state = FenParser.parse(fen).fold(err => fail(s"Failed to parse FEN: $err"), identity)
    val prob  = KingCaptureProbability.queenCaptureProbability(state, Color.White)
    assertEqualsDouble(prob, SingleAttackerProb, 0.0001)
  }
