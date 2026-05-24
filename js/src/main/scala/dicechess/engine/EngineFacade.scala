package dicechess.engine

import scala.scalajs.js.annotation.*
import scala.scalajs.js
import dicechess.engine.domain.*
import dicechess.engine.movegen.MoveGenerator
import scala.util.Random

/** The `EngineFacade` provides a JavaScript-friendly API to interact with the Dice Chess Scala engine.
  *
  * It exposes methods to generate bot moves, query piece types, fetch legal moves filtered by dice, and apply moves
  * directly to FEN strings.
  */
@JSExportTopLevel("EngineFacade")
object EngineFacade {

  /** Computes a bot move for the given FEN and dice roll.
    *
    * @param fen
    *   The current board state in FEN notation.
    * @param diceRoll
    *   The dice value (1-6) available for the move.
    * @param seed
    *   Optional random seed for deterministic move selection.
    * @return
    *   A dictionary containing `from` and `to` square notations, and an optional `promotion` piece, or `undefined` if
    *   no legal moves exist.
    * @example
    *   {{{
    *   const move = EngineFacade.getBotMove("rnbqkbnr/... w - - 0 1", 3, 12345);
    *   if (move) console.log(move.from, move.to);
    *   }}}
    */
  @JSExport
  def getBotMove(
      fen: String,
      diceRoll: Int,
      seed: js.UndefOr[Int] = js.undefined
  ): js.UndefOr[js.Dictionary[String]] = {
    FenParser.parse(fen) match {
      case Right(state) =>
        val moves = MoveGenerator.generateMoves(state, diceRoll)
        if (moves.isEmpty) {
          js.undefined
        } else {
          // The user specifically requested: "Always beat the king if it possible in legal moves"
          val kingCapture = moves.find(m => state.mailbox.get(m.toSquare).exists(_.pieceType == PieceType.King))

          val chosenMove = kingCapture.getOrElse {
            val rng = seed.toOption.map(new Random(_)).getOrElse(Random)
            moves(rng.nextInt(moves.length))
          }

          val isPromotion = chosenMove.isPromotion
          val dict        = js.Dictionary[String](
            "from" -> chosenMove.fromSquare.toNotation,
            "to"   -> chosenMove.toSquare.toNotation
          )

          if (isPromotion) {
            // Chessground promotion format is "q", "r", "b", "n"
            val promChar = chosenMove.flags match {
              case Move.KnightPromotion | Move.KnightPromoCapture => "n"
              case Move.BishopPromotion | Move.BishopPromoCapture => "b"
              case Move.RookPromotion | Move.RookPromoCapture     => "r"
              case Move.QueenPromotion | Move.QueenPromoCapture   => "q"
              case _                                              => "q"
            }
            dict.put("promotion", promChar)
          }

          dict
        }
      case Left(_) =>
        js.undefined
    }
  }

  /** Retrieves the dice value (1-6) of the piece at the specified square.
    *
    * @param fen
    *   The current board state in FEN notation.
    * @param square
    *   The algebraic notation of the square (e.g. "e2").
    * @return
    *   The integer dice value corresponding to the piece type (1=Pawn..6=King), or `undefined` if the square is empty
    *   or invalid.
    * @example
    *   {{{
    *   const pt = EngineFacade.getPieceTypeAt(fen, "e2"); // returns 1 for a pawn
    *   }}}
    */
  @JSExport
  def getPieceTypeAt(fen: String, square: String): js.UndefOr[Int] = {
    FenParser.parse(fen) match {
      case Right(state) =>
        Square.fromNotation(square) match {
          case Some(sq) =>
            state.mailbox
              .get(sq)
              .map(_.pieceType.diceValue)
              .fold(js.undefined)(v => v)
          case None => js.undefined
        }
      case Left(_) => js.undefined
    }
  }

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
    * @example
    *   {{{
    *   const newFen = EngineFacade.applyMove(fen, "e2", "e4", undefined);
    *   }}}
    */
  @JSExport
  def applyMove(fen: String, from: String, to: String, promotion: js.UndefOr[String]): js.UndefOr[String] = {
    FenParser.parse(fen) match {
      case Right(state) =>
        (Square.fromNotation(from), Square.fromNotation(to)) match {
          case (Some(fromSq), Some(toSq)) =>
            // Generate all pseudo-legal moves for standard generation (no dice roll constraint to find the human's move)
            val moves = MoveGenerator.generateAllMoves(state)

            val moveOpt = moves.find { m =>
              m.fromSquare == fromSq && m.toSquare == toSq &&
              (!m.isPromotion || promotion.isEmpty || {
                val promChar = m.flags match {
                  case Move.KnightPromotion | Move.KnightPromoCapture => "n"
                  case Move.BishopPromotion | Move.BishopPromoCapture => "b"
                  case Move.RookPromotion | Move.RookPromoCapture     => "r"
                  case Move.QueenPromotion | Move.QueenPromoCapture   => "q"
                  case _                                              => "q"
                }
                promChar == promotion.get
              })
            }

            moveOpt match {
              case Some(move) =>
                val newState = state.makeMove(move)
                FenParser.serialize(newState)
              case None =>
                js.undefined
            }
          case _ => js.undefined
        }
      case Left(_) =>
        js.undefined
    }
  }
}
