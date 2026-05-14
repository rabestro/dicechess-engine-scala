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
    // White has 4 knight moves. For each, Black has 4 knight moves (standard alternating turns).
    // Total nodes = 4 * 4 = 16
    val nodes = Perft.countNodes(state, 2, 2)
    assertEquals(nodes, 16L)
  }
