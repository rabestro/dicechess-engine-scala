package dicechess.engine.api

import dicechess.engine.domain.*
import dicechess.engine.movegen.LegalMovesFilter
import dicechess.engine.search.BotRegistry
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

  /** Returns all available bots (search algorithms) supported by the engine.
    *
    * @return
    *   An array of bot metadata objects.
    */
  @JSExport
  def getAvailableBots(): js.Array[js.Dynamic] =
    BotRegistry.availableBots.map { bot =>
      js.Dynamic.literal(
        id = bot.id,
        name = bot.name,
        description = bot.description,
        difficulty = bot.difficulty,
        isExperimental = bot.isExperimental
      )
    }.toJSArray

  /** Returns all legal moves as an array of UCI strings (e.g., ["e2e4", "e7e8q"]).
    *
    * Useful for pawn promotion logic on the frontend to know exactly which pieces are allowed.
    *
    * @param dfen
    *   The position in DiceChess Forsyth-Edwards Notation (including the dice pool).
    * @return
    *   An array of full UCI move strings.
    */
  @JSExport
  def getLegalUciMoves(dfen: String): js.Array[String] =
    if Option(dfen).isEmpty then js.Array()
    else
      FenParser.parse(dfen) match
        case Left(_)      => js.Array()
        case Right(state) =>
          val allMoves = LegalMovesFilter.filterMaximalMoves(state)
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
  def getPieceFromDice(dice: Int): String | Null =
    PieceType.fromDice(dice).map(_.asNotation).orNull

  /** Computes the best sequence of micro-moves for the given position.
    *
    * @param dfen
    *   The position in DiceChess Forsyth-Edwards Notation.
    * @param options
    *   Optional configuration (e.g.
    *   ```json
    *   { "algorithm": "greedy" }
    *   ```
    *   ).
    * @return
    *   An object containing the array of moves, score, and time taken.
    */
  @JSExport
  def getBestMove(dfen: String, options: js.UndefOr[js.Dynamic]): js.Dynamic =
    if Option(dfen).isEmpty then js.Dynamic.literal(moves = js.Array(), score = 0, timeTakenMs = 0)
    else
      val searchAlgo = resolveAlgorithm(options)

      FenParser.parse(dfen) match
        case Left(_)      => js.Dynamic.literal(moves = js.Array(), score = 0, timeTakenMs = 0)
        case Right(state) =>
          val start = System.currentTimeMillis()
          searchAlgo.findBestMove(state) match
            case None =>
              js.Dynamic.literal(
                moves = js.Array(),
                score = 0,
                timeTakenMs = (System.currentTimeMillis() - start).toInt
              )
            case Some(scoredSeq) =>
              val jsMoves = scoredSeq.moves.map { m =>
                js.Dynamic.literal(
                  from = m.fromSquare.toNotation,
                  to = m.toSquare.toNotation,
                  promotion = m.promotionPieceType.map(_.asNotation).orUndefined
                )
              }.toJSArray
              js.Dynamic.literal(
                moves = jsMoves,
                score = scoredSeq.score,
                timeTakenMs = (System.currentTimeMillis() - start).toInt
              )

  /** Applies a move to the given DFEN and returns the resulting state.
    *
    * @param dfen
    *   The starting board state in DiceChess Forsyth-Edwards Notation (DFEN).
    * @param from
    *   The algebraic notation of the starting square.
    * @param to
    *   The algebraic notation of the target square.
    * @param promotion
    *   The optional piece type to promote to (e.g. "q").
    * @return
    *   The updated DFEN string after applying the move, or `undefined` if the move is pseudo-illegal.
    */
  @JSExport
  def applyMove(dfen: String, from: String, to: String, promotion: js.UndefOr[String]): js.UndefOr[String] =
    dicechess.engine.EngineFacade.applyMove(dfen, from, to, promotion)

  /** Explicitly ends the current turn to mark a clear boundary between players in a multi-micro-move sequence.
    *
    * Frontend consumers should call this when a player has exhausted their dice or finished their sequence. This
    * function guarantees the game state is correctly finalized for the next player by cleaning up stale en-passant
    * targets from the previous turn, clearing the dice pool, and advancing the turn markers (color toggle, move
    * counts). It serves as the single entrypoint for advancing the DFEN state between player turns.
    *
    * @param dfen
    *   The current board state in DiceChess Forsyth-Edwards Notation.
    * @return
    *   The updated DFEN string finalized for the next player, or `undefined` if invalid.
    */
  @JSExport
  def endTurn(dfen: String): js.UndefOr[String] =
    dicechess.engine.EngineFacade.endTurn(dfen)

  /** Determines whether the bot should offer a double before its dice roll.
    */
  @JSExport
  def shouldBotOfferDouble(dfen: String, currentStake: Int, options: js.UndefOr[js.Dynamic]): Boolean =
    if Option(dfen).isEmpty then false
    else
      val searchAlgo = resolveAlgorithm(options)
      FenParser.parse(dfen) match
        case Left(_)      => false
        case Right(state) => searchAlgo.shouldOfferDouble(state, currentStake)

  /** Determines whether the bot should accept (Take) or decline (Drop) a double from the opponent.
    */
  @JSExport
  def shouldBotAcceptDouble(dfen: String, currentStake: Int, options: js.UndefOr[js.Dynamic]): Boolean =
    if Option(dfen).isEmpty then false
    else
      val searchAlgo = resolveAlgorithm(options)
      FenParser.parse(dfen) match
        case Left(_)      => false
        case Right(state) => searchAlgo.shouldAcceptDouble(state, currentStake)

  /** Determines whether the bot should offer a draw.
    */
  @JSExport
  def shouldBotOfferDraw(dfen: String, options: js.UndefOr[js.Dynamic]): Boolean =
    if Option(dfen).isEmpty then false
    else
      val searchAlgo = resolveAlgorithm(options)
      FenParser.parse(dfen) match
        case Left(_)      => false
        case Right(state) => searchAlgo.shouldOfferDraw(state)

  /** Determines whether the bot should accept a draw offered by the opponent.
    */
  @JSExport
  def shouldBotAcceptDraw(dfen: String, options: js.UndefOr[js.Dynamic]): Boolean =
    if Option(dfen).isEmpty then false
    else
      val searchAlgo = resolveAlgorithm(options)
      FenParser.parse(dfen) match
        case Left(_)      => false
        case Right(state) => searchAlgo.shouldAcceptDraw(state)

  private def resolveAlgorithm(options: js.UndefOr[js.Dynamic]): dicechess.engine.search.SearchAlgorithm =
    val algoName = options.toOption
      .filter(Option(_).isDefined)
      .flatMap { opt =>
        val alg = opt.selectDynamic("algorithm")
        if scala.scalajs.js.typeOf(alg) == "string" then Some(alg.asInstanceOf[String])
        else None
      }
      .getOrElse("greedy")
    BotRegistry.getAlgorithm(algoName).getOrElse(BotRegistry.defaultAlgorithm)
