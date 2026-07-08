package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite

import scala.util.Random

/** Behavioural tests for the 2-ply search, driven by the engine's own material evaluator (no model needed): the point
  * is the lookahead, not the evaluator. The key case is a position where the one-ply [[GreedySearch]] grabs material
  * and expectimax, seeing the reply, declines it.
  */
class ExpectimaxSearchSpec extends FunSuite:

  /** Material evaluator as a batch, so the search's injected `evalBatch` is exercised for real. */
  private val materialBatch: (Array[GameState], Color) => Array[Int] =
    (states, color) => states.map(state => Evaluator.evaluateMaterial(state, color))

  private def search(config: ExpectimaxConfig = ExpectimaxConfig()) =
    ExpectimaxSearch(materialBatch, config)

  private def parse(fen: String): GameState = FenParser.parse(fen).toOption.get

  private def uci(moves: List[Move]): String =
    moves.map(m => m.fromSquare.toNotation + m.toSquare.toNotation).mkString(" ")

  test("returns a legal turn when one exists"):
    val state = parse("1r4k1/p4ppp/8/8/8/8/5PPP/R5K1 w - - 0 1").withDicePool(List(2, 2, 4))
    assert(search().findBestMove(state, Random(0)).isDefined)

  test("is deterministic for a fixed seed"):
    val state = parse("1r4k1/p4ppp/8/8/8/8/5PPP/R5K1 w - - 0 1").withDicePool(List(2, 2, 4))
    val a     = search().findBestMove(state, Random(7)).map(s => uci(s.moves))
    val b     = search().findBestMove(state, Random(7)).map(s => uci(s.moves))
    assertEquals(a, b)

  test("plays an immediate king capture and scores it as a terminal win"):
    // A rook die lets the a1 rook sweep the open a-file onto the black king at a8.
    val state  = parse("k7/8/8/8/8/8/8/R3K3 w - - 0 1").withDicePool(List(1, 1, 4))
    val result = search().findBestMove(state, Random(0))
    assert(result.isDefined)
    val chosen = result.get
    assertEquals(chosen.score, SearchScoring.TerminalWinScore)
    assertEquals(chosen.moves.last.toSquare.toNotation, "a8")

  test("declines a material grab that hangs to the opponent's reply — greedy walks in, expectimax does not"):
    // White: Ra1, Kg1, pawns f2/g2/h2. Black: Rb8, Kg8, pawns f7/g7/h7, and a loose pawn on a7.
    // Dice 2,2,4: only the rook die (4) is usable (no knights, no pawn die), so every turn is one rook move.
    // Grabbing a7 (Rxa7) wins a pawn now but abandons the first rank; over the opponent's replies that is far worse
    // than keeping the rook home. GreedySearch takes the pawn; the 2-ply search refuses it.
    val state = parse("1r4k1/p4ppp/8/8/8/8/5PPP/R5K1 w - - 0 1").withDicePool(List(2, 2, 4))

    val greedyMove = GreedySearch.findBestMove(state, Random(0)).map(s => uci(s.moves))
    assertEquals(greedyMove, Some("a1a7"), "precondition: greedy grabs the pawn")

    val expectimaxMove = search().findBestMove(state, Random(0)).map(s => uci(s.moves))
    assert(
      expectimaxMove.exists(_ != "a1a7"),
      s"expected the 2-ply search to decline the hanging grab a1a7, got $expectimaxMove"
    )

  test("every evaluated leaf is from the mover's perspective, including forced passes"):
    // The opponent is a lone king: on every roll without a king die it has no legal move and must pass. All leaves —
    // ordinary replies and passes alike — must be scored with the mover to move, so an evaluator that reads
    // side-to-move never sees the opponent's turn. This guards the forced-pass leaf against a regression to the
    // pre-endTurn state (invisible to material, but wrong for any richer evaluator).
    val state = parse("4k3/8/8/8/8/8/8/R3K3 w - - 0 1").withDicePool(List(1, 4, 6))
    val checkingBatch: (Array[GameState], Color) => Array[Int] =
      (states, color) =>
        states.foreach(s => assert(s.activeColor == color, s"leaf must be from the mover's perspective"))
        states.map(s => Evaluator.evaluateMaterial(s, color))
    assert(ExpectimaxSearch(checkingBatch).findBestMove(state, Random(0)).isDefined)

  test("candidateLimit must be positive"):
    intercept[IllegalArgumentException](ExpectimaxConfig(candidateLimit = 0))
