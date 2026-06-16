package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite

/** Regression tests for issue #351: en passant is rejected unless it is the first micro-move.
  *
  * In Dice Chess a turn is up to three micro-moves, and the en-passant target set by the opponent's pawn double-step
  * (present in the incoming FEN) stays capturable throughout the whole turn — not only on the first micro-move. The
  * engine currently clears the en-passant square after the first intra-turn micro-move, so
  * [[TurnGenerator.generateAllLegalTurnPaths]] only produces paths where the en-passant capture comes first. Real
  * dicechess.com games that play the capture as the 2nd/3rd micro-move are therefore wrongly rejected (422 IllegalTurn
  * in dicechess-analytics).
  *
  * The positions and dice below are the two reproductions recorded in issue #351.
  */
class EnPassantMicroMoveSuite extends FunSuite:

  private def parse(fen: String): GameState =
    FenParser.parse(fen).fold(err => fail(s"Failed to parse FEN: $err"), identity)

  /** True if some legal turn path plays an en-passant capture as a non-first micro-move. */
  private def hasNonFirstEnPassant(paths: List[List[Move]]): Boolean =
    paths.exists(path => path.zipWithIndex.exists((move, i) => i > 0 && move.isEnPassant))

  // `.fail`: suspended until #351 is fixed. While the bug is present the body fails on the
  // non-first-en-passant assertion and munit records an expected failure (green). When the engine
  // is fixed the body passes and munit flips this red — the cue to drop `.fail` and lock it in.
  test("en passant is legal as the LAST micro-move (#351 case 1: ep d3, dice P/Q/Q)".fail) {
    // Black to move, en-passant target d3. The real game played [d8d6, d6c6, e4d3] (ep capture
    // last) and dicechess.com accepted it; the engine must offer ep beyond the first micro-move.
    val state = parse("r2q2r1/ppp1nk1p/2B2p2/3p3b/1P1Pp2P/4P2N/PP3P2/RNB4K b - d3 0 7")
      .withDicePool(List(1, 5, 5))
    val paths = TurnGenerator.generateAllLegalTurnPaths(state)
    assert(paths.exists(_.exists(_.isEnPassant)), "en-passant capture e4d3 is not generated at all")
    assert(hasNonFirstEnPassant(paths), "en passant is only generated as the first micro-move (#351)")
  }

  test("en passant is legal as the 2nd micro-move (#351 case 2: ep d3, dice P/N/R)".fail) {
    // Black to move, en-passant target d3. The real game played [b8c8, e4d3, g8f6] (ep capture
    // as the 2nd micro-move) and dicechess.com accepted it.
    val state = parse("1rNk2nr/1ppp1ppp/4q3/2b5/1n1Pp3/5N2/PPPQPPPP/1RBK1B1R b - d3 2 4")
      .withDicePool(List(1, 2, 4))
    val paths = TurnGenerator.generateAllLegalTurnPaths(state)
    assert(paths.exists(_.exists(_.isEnPassant)), "en-passant capture e4d3 is not generated at all")
    assert(hasNonFirstEnPassant(paths), "en passant is only generated as the first micro-move (#351)")
  }
