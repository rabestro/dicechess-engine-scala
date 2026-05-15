package dicechess.engine.movegen

import dicechess.engine.domain.{Square, Bitboard}

/** Precomputed attack tables for leaping pieces (Knights and Kings).
  *
  * Since leapers are not blocked by other pieces, their attacks can be completely pre-calculated at JVM startup for all
  * 64 squares.
  */
object LeaperAttacks:

  // File exclusion masks to prevent wrap-around bugs
  private val NotAFile: Bitboard  = Bitboard(0xfefefefefefefefeL)
  private val NotABFile: Bitboard = Bitboard(0xfcfcfcfcfcfcfcfcL)
  private val NotHFile: Bitboard  = Bitboard(0x7f7f7f7f7f7f7f7fL)
  private val NotGHFile: Bitboard = Bitboard(0x3f3f3f3f3f3f3f3fL)

  /** Precomputed attack bitboards for a Knight on any given square. */
  val knightAttacks: Array[Bitboard] = Array.tabulate(64)(computeKnightAttacks)

  /** Precomputed attack bitboards for a King on any given square. */
  val kingAttacks: Array[Bitboard] = Array.tabulate(64)(computeKingAttacks)

  /** Returns all squares attacked by a Knight on the given square. */
  inline def knightAttacksFor(sq: Square): Bitboard = knightAttacks(Square.index(sq))

  /** Returns all squares attacked by a King on the given square. */
  inline def kingAttacksFor(sq: Square): Bitboard = kingAttacks(Square.index(sq))

  private def computeKnightAttacks(sqIndex: Int): Bitboard =
    val b   = Bitboard.fromSquare(Square.fromIndex(sqIndex))
    val nne = (b & NotHFile) << 17
    val nnw = (b & NotAFile) << 15
    val ene = (b & NotGHFile) << 10
    val wnw = (b & NotABFile) << 6
    val ese = (b & NotGHFile) >>> 6
    val wsw = (b & NotABFile) >>> 10
    val sse = (b & NotHFile) >>> 15
    val ssw = (b & NotAFile) >>> 17

    nne | nnw | ene | wnw | ese | wsw | sse | ssw

  private def computeKingAttacks(sqIndex: Int): Bitboard =
    val b  = Bitboard.fromSquare(Square.fromIndex(sqIndex))
    val n  = b << 8
    val s  = b >>> 8
    val e  = (b & NotHFile) << 1
    val w  = (b & NotAFile) >>> 1
    val ne = (b & NotHFile) << 9
    val nw = (b & NotAFile) << 7
    val se = (b & NotHFile) >>> 7
    val sw = (b & NotAFile) >>> 9

    n | s | e | w | ne | nw | se | sw
