package dicechess.engine.movegen

import dicechess.engine.domain.{Color, Bitboard}

/** Highly optimized pawn move generation using Bitboard arithmetic.
  *
  * Unlike leaping pieces which are generated per-square, pawn pushes and attacks can be calculated in parallel for all
  * pawns of a given color simultaneously using bitwise shifts.
  */
object PawnGeneration:

  // File exclusion masks to prevent wrap-around bugs during diagonal captures
  private val NotAFile: Bitboard = Bitboard(0xfefefefefefefefeL)
  private val NotHFile: Bitboard = Bitboard(0x7f7f7f7f7f7f7f7fL)

  // Rank masks to determine if a double-push is legal
  private val Rank3: Bitboard = Bitboard(0x0000000000ff0000L)
  private val Rank6: Bitboard = Bitboard(0x0000ff0000000000L)

  /** Computes the single forward push for all pawns.
    *
    * @param pawns
    *   Bitboard of all pawns of the given color.
    * @param emptySquares
    *   Bitboard of all currently empty squares.
    * @return
    *   A Bitboard of the squares the pawns will land on after a single push.
    */
  inline def singlePushes(pawns: Bitboard, emptySquares: Bitboard, color: Color): Bitboard =
    val pushes = if color.isWhite then pawns << 8 else pawns >>> 8
    pushes & emptySquares

  /** Computes the double forward push for all pawns starting from their initial rank.
    *
    * @param singlePushes
    *   The result of the `singlePushes` method.
    * @param emptySquares
    *   Bitboard of all currently empty squares.
    * @return
    *   A Bitboard of the squares the pawns will land on after a double push.
    */
  inline def doublePushes(singlePushes: Bitboard, emptySquares: Bitboard, color: Color): Bitboard =
    val pushes = if color.isWhite then (singlePushes & Rank3) << 8 else (singlePushes & Rank6) >>> 8
    pushes & emptySquares

  /** Computes all diagonal captures towards the East (H-file).
    *
    * @param pawns
    *   Bitboard of pawns.
    * @param enemies
    *   Bitboard of enemy pieces (or En Passant target).
    */
  inline def eastCaptures(pawns: Bitboard, enemies: Bitboard, color: Color): Bitboard =
    val attacks = if color.isWhite then (pawns & NotHFile) << 9 else (pawns & NotHFile) >>> 7
    attacks & enemies

  /** Computes all diagonal captures towards the West (A-file).
    *
    * @param pawns
    *   Bitboard of pawns.
    * @param enemies
    *   Bitboard of enemy pieces (or En Passant target).
    */
  inline def westCaptures(pawns: Bitboard, enemies: Bitboard, color: Color): Bitboard =
    val attacks = if color.isWhite then (pawns & NotAFile) << 7 else (pawns & NotAFile) >>> 9
    attacks & enemies

  /** Computes all attacked squares by pawns, regardless of whether there is an enemy there. Used primarily to determine
    * if the King is in check or squares are unsafe.
    */
  inline def anyAttacks(pawns: Bitboard, color: Color): Bitboard =
    val east = if color.isWhite then (pawns & NotHFile) << 9 else (pawns & NotHFile) >>> 7
    val west = if color.isWhite then (pawns & NotAFile) << 7 else (pawns & NotAFile) >>> 9
    east | west
