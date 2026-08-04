package dicechess.engine.bench

import dicechess.engine.domain.*
import dicechess.engine.search.TurnGenerator
import munit.FunSuite

/** Pins the corpus-row parsing behind [[PreRankRecallProbeMain]].
  *
  * The probe's headline number — how often the candidate cut changes the search's decision — is computed over positions
  * built by this function. A dropped or mis-mapped die would shrink each position's legal-turn count, quietly moving
  * both the "exposed to the cut" population and the rate measured over it, with nothing in the output to show for it.
  */
class PreRankRecallProbeSpec extends FunSuite:

  private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -"

  test("each die letter maps to its piece type, in order"):
    val state = PreRankRecallProbeMain.parseState(startFen, "PNB").get
    assertEquals(
      state.dicePool.toList,
      List(PieceType.Pawn.diceValue, PieceType.Knight.diceValue, PieceType.Bishop.diceValue)
    )

  test("all six letters are recognised, across two rolls"):
    // Two rolls rather than one six-letter string, because a pool holds exactly three dice — see the truncation test.
    val first  = PreRankRecallProbeMain.parseState(startFen, "PNB").get.dicePool.toSet
    val second = PreRankRecallProbeMain.parseState(startFen, "RQK").get.dicePool.toSet
    assertEquals(first ++ second, PieceType.all.map(_.diceValue).toSet)

  test("a pool longer than three dice is silently truncated"):
    // Not a defect of the probe but of the platform it sits on: GameFlags packs three dice into bit fields via
    // lift(0..2), so a fourth is dropped without complaint. Harmless for this probe — a Dice Chess roll is always
    // three — but pinned here so nobody later feeds it a longer pool and trusts the result.
    val state = PreRankRecallProbeMain.parseState(startFen, "PNBRQK").get
    assertEquals(state.dicePool.size, 3)
    assertEquals(
      state.dicePool.toList,
      List(PieceType.Pawn.diceValue, PieceType.Knight.diceValue, PieceType.Bishop.diceValue)
    )

  test("lowercase is accepted — the corpus stores the roll case-shifted by side to move"):
    val upper = PreRankRecallProbeMain.parseState(startFen, "PQK").get
    val lower = PreRankRecallProbeMain.parseState(startFen, "pqk").get
    assertEquals(lower.dicePool.toList, upper.dicePool.toList)

  test("a repeated die is kept, not collapsed"):
    // The pool is a multiset: three pawns is a legitimate roll and must not become one.
    assertEquals(PreRankRecallProbeMain.parseState(startFen, "PPP").get.dicePool.size, 3)

  test("an empty or unrecognised roll yields no position rather than a dice-free one"):
    // A position with an empty pool would generate no legal turns and silently drop out of the sample.
    assertEquals(PreRankRecallProbeMain.parseState(startFen, ""), None)
    assertEquals(PreRankRecallProbeMain.parseState(startFen, "xyz"), None)

  test("an unparseable FEN yields no position"):
    assertEquals(PreRankRecallProbeMain.parseState("not-a-fen", "PNB"), None)

  test("the parsed position is playable — the probe's premise"):
    val state = PreRankRecallProbeMain.parseState(startFen, "PNB").get
    assert(TurnGenerator.generateAllLegalTurnPaths(state).nonEmpty)
