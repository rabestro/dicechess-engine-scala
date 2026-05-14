package dicechess.engine.movegen

import munit.FunSuite
import dicechess.engine.domain.*

class PerftSpec extends FunSuite:

  val initialState: GameState =
    FenParser.parse(FenParser.InitialPosition).getOrElse(sys.error("Failed to parse initial position"))

  test("Perft Depth 1 - Starting Position - Pawns (1)") {
    assertEquals(Perft.countNodes(initialState, 1, 1), 16L)
  }

  test("Perft Depth 1 - Starting Position - Knights (2)") {
    assertEquals(Perft.countNodes(initialState, 2, 1), 4L)
  }

  test("Perft Depth 1 - Starting Position - Blocked Pieces (3, 4, 5, 6)") {
    assertEquals(Perft.countNodes(initialState, 3, 1), 0L) // Bishop
    assertEquals(Perft.countNodes(initialState, 4, 1), 0L) // Rook
    assertEquals(Perft.countNodes(initialState, 5, 1), 0L) // Queen
    assertEquals(Perft.countNodes(initialState, 6, 1), 0L) // King
  }

  test("Perft Depth 2 - Starting Position - Knights (2)") {
    // White has 4 knight moves. For each, Black has 4 knight moves (standard alternating turns).
    // Total nodes = 4 * 4 = 16
    assertEquals(Perft.countNodes(initialState, 2, 2), 16L)
  }
