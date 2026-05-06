package dicechess.engine.domain

import munit.FunSuite

class ModelsSpec extends FunSuite:

  test("Square.fromNotation should successfully parse valid coordinates") {
    val maybeSquare = Square.fromNotation("e4")
    assert(maybeSquare.isDefined)
    assertEquals(maybeSquare.get.file, 'e')
    assertEquals(maybeSquare.get.rank, 4)
  }

  test("Square.fromNotation should return None for invalid coordinates") {
    assert(Square.fromNotation("i9").isEmpty)
    assert(Square.fromNotation("a").isEmpty)
    assert(Square.fromNotation("e10").isEmpty)
  }

  test("MicroMove.toNotation should format correctly") {
    val from = Square('g', 1)
    val to = Square('f', 3)
    val move = MicroMove(from, to)
    assertEquals(move.toNotation, "g1f3")
  }

  test("PieceType.fromDice should map values correctly") {
    assertEquals(PieceType.fromDice(1), Some(PieceType.Pawn))
    assertEquals(PieceType.fromDice(6), Some(PieceType.King))
    assertEquals(PieceType.fromDice(0), None)
  }
