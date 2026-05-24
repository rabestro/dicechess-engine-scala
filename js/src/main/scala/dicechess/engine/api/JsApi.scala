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

  /** Returns all legal moves as an array of UCI strings (e.g., ["e2e4", "e7e8q"]).
    *
    * Useful for pawn promotion logic on the frontend to know exactly which pieces are allowed.
    *
    * @param fen
    *   The position in Forsyth-Edwards Notation.
    * @param dice
    *   An array of available dice roll results (1-6).
    * @return
    *   An array of full UCI move strings.
    */
  @JSExport
  def getLegalUciMoves(fen: String, dice: js.Array[Int]): js.Array[String] =
    FenParser.parse(fen) match
      case Left(_)      => js.Array()
      case Right(state) =>
        val allMoves = LegalMovesFilter.filterMaximalMoves(state, dice.toList)
        allMoves.map { m =>
          val base = m.fromSquare.toNotation + m.toSquare.toNotation
          m.promotionPieceType.map(pt => base + pt.asNotation).getOrElse(base)
        }.toJSArray

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

  /** Applies a move to the given FEN and returns the resulting state.
    *
    * @param fen
    *   The starting board state in FEN notation.
    * @param from
    *   The algebraic notation of the starting square.
    * @param to
    *   The algebraic notation of the target square.
    * @param promotion
    *   The optional piece type to promote to (e.g. "q").
    * @return
    *   The updated FEN string after applying the move, or `undefined` if the move is pseudo-illegal.
    */
  @JSExport
  def applyMove(fen: String, from: String, to: String, promotion: js.UndefOr[String]): js.UndefOr[String] =
    dicechess.engine.EngineFacade.applyMove(fen, from, to, promotion)
