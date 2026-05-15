package dicechess.engine.api

import dicechess.engine.domain.*
import dicechess.engine.movegen.MoveGenerator
import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.JSConverters.*

/** JavaScript API for the Dice Chess Engine.
  *
  * This object is exported to JavaScript as `DiceChess`. It provides high-level functions for FEN parsing, move
  * generation, and validation, optimized for UI consumption.
  */
@JSExportTopLevel("DiceChess")
object JsApi:

  /** Returns all legal moves for a given position and dice roll.
    *
    * The output is a JavaScript object where keys are origin squares (e.g., "e2") and values are arrays of destination
    * squares (e.g., ["e3", "e4"]). This format is directly compatible with the `dests` property of Chessground.
    *
    * @param fen
    *   The position in Forsyth-Edwards Notation.
    * @param dice
    *   The dice roll result (1-6).
    * @return
    *   A dictionary of legal destination squares grouped by origin.
    */
  @JSExport
  def getLegalMoves(fen: String, dice: Int): js.Dictionary[js.Array[String]] =
    FenParser.parse(fen) match
      case Left(_) => js.Dictionary.empty
      case Right(state) =>
        val moves = MoveGenerator.generateMoves(state, dice)
        // Group moves by origin square and convert to notation strings
        moves
          .groupBy(_.fromSquare)
          .map { (from, mvs) =>
            from.toNotation -> mvs.map(_.toSquare.toNotation).distinct.toJSArray
          }
          .toJSDictionary

  /** Validates if a move is legal for the given position and dice roll.
    *
    * @param fen
    *   The position in Forsyth-Edwards Notation.
    * @param dice
    *   The dice roll result (1-6).
    * @param from
    *   The origin square notation (e.g., "e2").
    * @param to
    *   The destination square notation (e.g., "e4").
    * @return
    *   `true` if the move is legal, `false` otherwise.
    */
  @JSExport
  def isValidMove(fen: String, dice: Int, from: String, to: String): Boolean =
    FenParser.parse(fen) match
      case Left(_) => false
      case Right(state) =>
        val moves = MoveGenerator.generateMoves(state, dice)
        moves.exists(m => m.fromSquare.toNotation == from && m.toSquare.toNotation == to)

  /** Returns the piece type associated with a dice roll.
    *
    * @param dice
    *   The dice roll (1-6).
    * @return
    *   The piece notation (p, n, b, r, q, k) or null if invalid.
    */
  @JSExport
  def getPieceFromDice(dice: Int): String =
    PieceType.fromDice(dice).map(_.asNotation).getOrElse(null)
