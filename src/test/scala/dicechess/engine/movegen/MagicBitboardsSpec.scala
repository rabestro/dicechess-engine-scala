package dicechess.engine.movegen

import munit.FunSuite
import dicechess.engine.domain.{Bitboard, Square}

class MagicBitboardsSpec extends FunSuite:

  test("Bishop attacks on empty board") {
    val sq        = Square.fromIndex(28) // e4
    val occupancy = Bitboard.empty
    val attacks   = MagicBitboards.bishopAttacks(sq, occupancy)
    val expected  = MagicBitboards.bishopAttacksClassic(sq, occupancy)
    assertEquals(attacks, expected)
  }

  test("Bishop attacks with blockers") {
    val sq = Square.fromIndex(28) // e4
    // Blockers on d3, f5
    val occupancy = Bitboard.fromSquare(Square.fromIndex(19)) | Bitboard.fromSquare(Square.fromIndex(37))
    val attacks   = MagicBitboards.bishopAttacks(sq, occupancy)
    val expected  = MagicBitboards.bishopAttacksClassic(sq, occupancy)
    assertEquals(attacks, expected)
  }

  test("Rook attacks on empty board") {
    val sq        = Square.fromIndex(28) // e4
    val occupancy = Bitboard.empty
    val attacks   = MagicBitboards.rookAttacks(sq, occupancy)
    val expected  = MagicBitboards.rookAttacksClassic(sq, occupancy)
    assertEquals(attacks, expected)
  }

  test("Rook attacks with blockers") {
    val sq = Square.fromIndex(28) // e4
    // Blockers on e2, e6, c4, g4
    val occupancy = Bitboard.fromSquare(Square.fromIndex(12)) |
      Bitboard.fromSquare(Square.fromIndex(44)) |
      Bitboard.fromSquare(Square.fromIndex(26)) |
      Bitboard.fromSquare(Square.fromIndex(30))
    val attacks  = MagicBitboards.rookAttacks(sq, occupancy)
    val expected = MagicBitboards.rookAttacksClassic(sq, occupancy)
    assertEquals(attacks, expected)
  }

  test("Queen attacks") {
    val sq        = Square.fromIndex(28) // e4
    val occupancy = Bitboard.empty
    val attacks   = MagicBitboards.queenAttacks(sq, occupancy)
    val expected = MagicBitboards.bishopAttacksClassic(sq, occupancy) | MagicBitboards.rookAttacksClassic(sq, occupancy)
    assertEquals(attacks, expected)
  }

  test("Corner cases (A1)") {
    val sq        = Square.fromIndex(0) // a1
    val occupancy = Bitboard.empty
    assertEquals(MagicBitboards.bishopAttacks(sq, occupancy), MagicBitboards.bishopAttacksClassic(sq, occupancy))
    assertEquals(MagicBitboards.rookAttacks(sq, occupancy), MagicBitboards.rookAttacksClassic(sq, occupancy))
  }

  test("Corner cases (H8)") {
    val sq        = Square.fromIndex(63) // h8
    val occupancy = Bitboard.empty
    assertEquals(MagicBitboards.bishopAttacks(sq, occupancy), MagicBitboards.bishopAttacksClassic(sq, occupancy))
    assertEquals(MagicBitboards.rookAttacks(sq, occupancy), MagicBitboards.rookAttacksClassic(sq, occupancy))
  }
