package dicechess.engine

import scala.scalajs.js.annotation.*
import scala.scalajs.js
import dicechess.engine.domain.*
import dicechess.engine.movegen.MoveGenerator
import scala.util.Random

@JSExportTopLevel("EngineFacade")
object EngineFacade {

  @JSExport
  def getBotMove(fen: String, diceRoll: Int): js.UndefOr[js.Dictionary[String]] = {
    FenParser.parse(fen) match {
      case Right(state) =>
        val moves = MoveGenerator.generateMoves(state, diceRoll)
        if (moves.isEmpty) {
          js.undefined
        } else {
          // The user specifically requested: "Always beat the king if it possible in legal moves"
          val kingCapture = moves.find(m => state.mailbox.get(m.toSquare).exists(_.pieceType == PieceType.King))

          val chosenMove = kingCapture.getOrElse {
            moves(Random.nextInt(moves.length))
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

  @JSExport
  def getPieceTypeAt(fen: String, square: String): js.UndefOr[Int] = {
    FenParser.parse(fen) match {
      case Right(state) =>
        Square.fromNotation(square) match {
          case Some(sq) =>
            state.mailbox
              .get(sq)
              .map(_.pieceType match {
                case PieceType.Pawn   => 1
                case PieceType.Knight => 2
                case PieceType.Bishop => 3
                case PieceType.Rook   => 4
                case PieceType.Queen  => 5
                case PieceType.King   => 6
                case _                => -1
              })
              .filter(_ != -1)
              .fold(js.undefined)(v => v)
          case None => js.undefined
        }
      case Left(_) => js.undefined
    }
  }

  @JSExport
  def getLegalDests(fen: String, diceRolls: js.Array[Int]): js.UndefOr[js.Dictionary[js.Array[String]]] = {
    FenParser.parse(fen) match {
      case Right(state) =>
        val dests = js.Dictionary[js.Array[String]]()

        // Compute the unique set of dice available
        val uniqueDice = diceRolls.toSet

        uniqueDice.foreach { dice =>
          val moves = MoveGenerator.generateMoves(state, dice)
          moves.foreach { move =>
            val fromStr = move.fromSquare.toNotation
            val toStr   = move.toSquare.toNotation
            if (!dests.contains(fromStr)) {
              dests.put(fromStr, js.Array())
            }
            // avoid duplicates in dests array (promotions generate 4 moves to same square)
            val arr = dests(fromStr)
            if (!arr.contains(toStr)) {
              arr.push(toStr)
            }
          }
        }

        dests
      case Left(_) => js.undefined
    }
  }

  @JSExport
  def applyMove(fen: String, from: String, to: String, promotion: js.UndefOr[String]): js.UndefOr[String] = {
    FenParser.parse(fen) match {
      case Right(state) =>
        (Square.fromNotation(from), Square.fromNotation(to)) match {
          case (Some(fromSq), Some(toSq)) =>
            // Generate all pseudo-legal moves for standard standard generation (no dice roll constraint to find the human's move)
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
