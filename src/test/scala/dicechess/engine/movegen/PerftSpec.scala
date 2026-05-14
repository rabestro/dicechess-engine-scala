package dicechess.engine.movegen

import munit.FunSuite
import dicechess.engine.domain.*

class PerftSpec extends FunSuite:

  test("Perft Depth 1 - Starting Position - Pawns (1)") {
    val state = FenParser.parse(FenParser.InitialPosition).getOrElse(fail("Parse failed"))
    val nodes = Perft.countNodes(state, 1, 1)
    assertEquals(nodes, 16L)
  }

  test("Perft Depth 1 - Starting Position - Knights (2)") {
    val state = FenParser.parse(FenParser.InitialPosition).getOrElse(fail("Parse failed"))
    val nodes = Perft.countNodes(state, 2, 1)
    assertEquals(nodes, 4L)
  }

  test("Perft Depth 1 - Starting Position - Blocked Pieces (3, 4, 5, 6)") {
    val state = FenParser.parse(FenParser.InitialPosition).getOrElse(fail("Parse failed"))
    assertEquals(Perft.countNodes(state, 3, 1), 0L) // Bishop
    assertEquals(Perft.countNodes(state, 4, 1), 0L) // Rook
    assertEquals(Perft.countNodes(state, 5, 1), 0L) // Queen
    assertEquals(Perft.countNodes(state, 6, 1), 0L) // King
  }

  test("Perft Depth 2 - Starting Position - Knights (2)") {
    val state = FenParser.parse(FenParser.InitialPosition).getOrElse(fail("Parse failed"))
    // Each of the 4 knight moves leads back to a position where the same 2 knights
    // (now one of them moved) can move again.
    // Let's trace:
    // b1-a3: knights at a3, g1. Moves from a3: b1, c4, b5. Moves from g1: f3, h3. (5 moves)
    // b1-c3: knights at c3, g1. Moves from c3: b1, a4, b5, d5, e4. Moves from g1: f3, h3. (7 moves)
    // g1-f3: knights at b1, f3. Moves from b1: a3, c3. Moves from f3: g1, e5, d4, h4, g5. (7 moves)
    // g1-h3: knights at b1, h3. Moves from b1: a3, c3. Moves from h3: g1, f4, g5. (5 moves)
    // Total nodes at depth 2 = 5 + 7 + 7 + 5 = 24
    val nodes = Perft.countNodes(state, 2, 2)
    assertEquals(nodes, 24L)
  }
