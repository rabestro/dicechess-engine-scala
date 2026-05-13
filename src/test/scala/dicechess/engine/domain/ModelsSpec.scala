package dicechess.engine.domain

import munit.FunSuite

class ModelsSpec extends FunSuite:

  test("Color.opponent should toggle correctly using bitwise logic") {
    assertEquals(Color.White.opponent, Color.Black)
    assertEquals(Color.Black.opponent, Color.White)
    assert(Color.White.isWhite)
    assert(Color.Black.isBlack)
  }

  test("PieceType.fromDice should map values correctly") {
    assertEquals(PieceType.fromDice(1), Some(PieceType.Pawn))
    assertEquals(PieceType.fromDice(6), Some(PieceType.King))
    assertEquals(PieceType.fromDice(0), None)
  }

  test("Piece packing should preserve both Color and PieceType perfectly") {
    val whitePawn = Piece(Color.White, PieceType.Pawn)
    assertEquals(whitePawn.color, Color.White)
    assertEquals(whitePawn.pieceType, PieceType.Pawn)

    val blackKnight = Piece(Color.Black, PieceType.Knight)
    assertEquals(blackKnight.color, Color.Black)
    assertEquals(blackKnight.pieceType, PieceType.Knight)

    val blackKing = Piece(Color.Black, PieceType.King)
    assertEquals(blackKing.color, Color.Black)
    assertEquals(blackKing.pieceType, PieceType.King)
  }

  test("Square index calculations and notation mapping must be bidirectional") {
    val a1 = Square('a', 1)
    assertEquals(a1.index, 0)
    assertEquals(a1.file, 'a')
    assertEquals(a1.rank, 1)

    val h8 = Square('h', 8)
    assertEquals(h8.index, 63)
    assertEquals(h8.file, 'h')
    assertEquals(h8.rank, 8)

    val e4 = Square.fromIndex(28) // ((4-1)*8) + (5-1) = 24 + 4 = 28
    assertEquals(e4.file, 'e')
    assertEquals(e4.rank, 4)
  }

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

  test("MicroMove.toNotation should format basic moves correctly") {
    val from = Square('g', 1)
    val to   = Square('f', 3)
    val move = MicroMove(from, to)
    assertEquals(move.toNotation, "g1f3")
  }

  test("MicroMove packing should preserve origin, destination, and promotion piece") {
    val from = Square('e', 7)
    val to   = Square('e', 8)
    val move = MicroMove(from, to, Some(PieceType.Queen))

    assertEquals(move.from.toNotation, "e7")
    assertEquals(move.to.toNotation, "e8")
    assertEquals(move.promotion, Some(PieceType.Queen))
    assertEquals(move.toNotation, "e7e8q")
  }

  test("Bitboard properties: empty, full, add, remove, and contains") {
    val empty = Bitboard.empty
    assert(empty.isEmpty)
    assertEquals(empty.count, 0)

    val a1 = Square('a', 1)
    val h8 = Square('h', 8)

    val b1 = empty.add(a1)
    assert(b1.contains(a1))
    assert(!b1.contains(h8))
    assertEquals(b1.count, 1)

    val b2 = b1.add(h8)
    assert(b2.contains(a1))
    assert(b2.contains(h8))
    assertEquals(b2.count, 2)

    val b3 = b2.remove(a1)
    assert(!b3.contains(a1))
    assert(b3.contains(h8))
    assertEquals(b3.count, 1)

    val full = Bitboard.full
    assertEquals(full.count, 64)
    assert(full.contains(a1) && full.contains(h8))
  }

  test("Bitboard bitwise operations (AND, OR, XOR, NOT)") {
    val sq1 = Bitboard.fromSquare(Square('e', 4))
    val sq2 = Bitboard.fromSquare(Square('d', 5))

    val union = sq1 | sq2
    assert(union.contains(Square('e', 4)))
    assert(union.contains(Square('d', 5)))
    assertEquals(union.count, 2)

    val intersection = union & sq1
    assertEquals(intersection, sq1)

    val symDiff = union ^ sq1
    assertEquals(symDiff, sq2)

    val complement = ~union
    assert(!complement.contains(Square('e', 4)))
    assert(!complement.contains(Square('d', 5)))
    assertEquals(complement.count, 62)
  }
