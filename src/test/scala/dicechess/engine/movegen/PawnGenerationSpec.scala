package dicechess.engine.movegen

import munit.FunSuite
import dicechess.engine.domain.{Square, Bitboard, Color}

class PawnGenerationSpec extends FunSuite:

  test("White single and double pushes from starting rank") {
    val pawns        = Bitboard.fromSquare(Square('e', 2)) | Bitboard.fromSquare(Square('a', 2))
    val emptySquares = ~Bitboard.empty // All squares empty

    val single = PawnGeneration.singlePushes(pawns, emptySquares, Color.White)
    assertEquals(single.count, 2)
    assert(single.contains(Square('e', 3)))
    assert(single.contains(Square('a', 3)))

    val double = PawnGeneration.doublePushes(single, emptySquares, Color.White)
    assertEquals(double.count, 2)
    assert(double.contains(Square('e', 4)))
    assert(double.contains(Square('a', 4)))
  }

  test("Black single and double pushes from starting rank") {
    val pawns        = Bitboard.fromSquare(Square('d', 7)) | Bitboard.fromSquare(Square('h', 7))
    val emptySquares = ~Bitboard.empty

    val single = PawnGeneration.singlePushes(pawns, emptySquares, Color.Black)
    assertEquals(single.count, 2)
    assert(single.contains(Square('d', 6)))
    assert(single.contains(Square('h', 6)))

    val double = PawnGeneration.doublePushes(single, emptySquares, Color.Black)
    assertEquals(double.count, 2)
    assert(double.contains(Square('d', 5)))
    assert(double.contains(Square('h', 5)))
  }

  test("Pushes should be blocked by non-empty squares") {
    val pawns = Bitboard.fromSquare(Square('e', 2))
    // e3 is occupied, so emptySquares does NOT contain e3
    val emptySquares = ~Bitboard.fromSquare(Square('e', 3))

    val single = PawnGeneration.singlePushes(pawns, emptySquares, Color.White)
    assert(single.isEmpty) // Blocked!

    val double = PawnGeneration.doublePushes(single, emptySquares, Color.White)
    assert(double.isEmpty) // Double push also fails if single push is blocked
  }

  test("Double pushes should only work from starting ranks") {
    val pawns        = Bitboard.fromSquare(Square('e', 3))
    val emptySquares = ~Bitboard.empty

    val single = PawnGeneration.singlePushes(pawns, emptySquares, Color.White)
    assert(single.contains(Square('e', 4)))

    val double = PawnGeneration.doublePushes(single, emptySquares, Color.White)
    assert(double.isEmpty) // e3 is not starting rank, cannot double push to e5
  }

  test("White pawn captures and wrap-around prevention") {
    // Pawn on H-file should NOT wrap around to A-file when attacking East
    val pawns   = Bitboard.fromSquare(Square('h', 2))
    val enemies = ~Bitboard.empty

    val east = PawnGeneration.eastCaptures(pawns, enemies, Color.White)
    assert(east.isEmpty) // Blocked by NotHFile mask

    val west = PawnGeneration.westCaptures(pawns, enemies, Color.White)
    assert(west.contains(Square('g', 3)))
  }

  test("Black pawn captures and wrap-around prevention") {
    // Pawn on A-file should NOT wrap around to H-file when attacking West
    val pawns   = Bitboard.fromSquare(Square('a', 7))
    val enemies = ~Bitboard.empty

    val west = PawnGeneration.westCaptures(pawns, enemies, Color.Black)
    assert(west.isEmpty) // Blocked by NotAFile mask

    val east = PawnGeneration.eastCaptures(pawns, enemies, Color.Black)
    assert(east.contains(Square('b', 6)))
  }
