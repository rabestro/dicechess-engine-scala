package dicechess.engine.api

import dicechess.engine.domain.*
import dicechess.engine.movegen.LegalMovesFilter
import dicechess.engine.search.{GreedySearch, RandomSearch}
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

  /** Returns all legal moves for a given position and a set of available dice rolls.
    *
    * The output is a JavaScript object where keys are origin squares (e.g., "e2") and values are arrays of destination
    * squares (e.g., ["e3", "e4"]). This format is directly compatible with the `dests` property of Chessground.
    *
    * @param fen
    *   The position in Forsyth-Edwards Notation.
    * @param dice
    *   An array of available dice roll results (1-6).
    * @return
    *   A dictionary of legal destination squares grouped by origin.
    */
  @JSExport
  def getLegalMoves(fen: String, dice: js.Array[Int]): js.Dictionary[js.Array[String]] =
    FenParser.parse(fen) match
      case Left(_)      => js.Dictionary.empty
      case Right(state) =>
        // Filter legal moves according to the Maximum Micro-moves Rule
        val allMoves = LegalMovesFilter.filterMaximalMoves(state, dice.toList)

        // Group moves by origin square and convert to notation strings
        allMoves
          .groupBy(_.fromSquare)
          .map { (from, mvs) =>
            from.toNotation -> mvs.map(_.toSquare.toNotation).distinct.toJSArray
          }
          .toJSDictionary

  /** Validates if a move is legal for the given position and any of the available dice rolls.
    *
    * @param fen
    *   The position in Forsyth-Edwards Notation.
    * @param dice
    *   An array of available dice roll results (1-6).
    * @param from
    *   The origin square notation (e.g., "e2").
    * @param to
    *   The destination square notation (e.g., "e4").
    * @return
    *   `true` if the move is legal for at least one of the dice, `false` otherwise.
    */
  @JSExport
  def isValidMove(fen: String, dice: js.Array[Int], from: String, to: String): Boolean =
    FenParser.parse(fen) match
      case Left(_)      => false
      case Right(state) =>
        val moves = LegalMovesFilter.filterMaximalMoves(state, dice.toList)
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

  /** Computes the best sequence of micro-moves for the given position and dice.
    *
    * @param fen
    *   The position in Forsyth-Edwards Notation.
    * @param dice
    *   An array of available dice roll results (1-6).
    * @param options
    *   Optional configuration (e.g. `{{{ { algorithm: "greedy" } }}}`).
    * @return
    *   An object containing the array of moves, score, and time taken.
    */
  @JSExport
  def getBestMove(fen: String, dice: js.Array[Int], options: js.UndefOr[js.Dynamic]): js.Dynamic =
    val algoName = options.toOption
      .filter(_ != null)
      .flatMap { opt =>
        val alg = opt.selectDynamic("algorithm")
        if scala.scalajs.js.typeOf(alg) == "string" then Some(alg.asInstanceOf[String])
        else None
      }
      .getOrElse("greedy")

    val searchAlgo = algoName.toLowerCase match
      case "random" => RandomSearch
      case _        => GreedySearch

    FenParser.parse(fen) match
      case Left(_)      => js.Dynamic.literal(moves = js.Array(), score = 0, timeTakenMs = 0)
      case Right(state) =>
        val start = System.currentTimeMillis()
        searchAlgo.findBestMove(state, dice.toList) match
          case None =>
            js.Dynamic.literal(moves = js.Array(), score = 0, timeTakenMs = (System.currentTimeMillis() - start).toInt)
          case Some(scoredSeq) =>
            val jsMoves = scoredSeq.moves.map { m =>
              js.Dynamic.literal(
                from = m.fromSquare.toNotation,
                to = m.toSquare.toNotation,
                promotion = m.promotionPieceType.map(_.asNotation).getOrElse(null)
              )
            }.toJSArray
            js.Dynamic.literal(
              moves = jsMoves,
              score = scoredSeq.score,
              timeTakenMs = (System.currentTimeMillis() - start).toInt
            )
