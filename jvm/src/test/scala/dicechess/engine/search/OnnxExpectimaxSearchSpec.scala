package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite

import scala.util.Random

/** Plumbing checks for the ONNX-backed 2-ply bot against the throwaway synthetic model (no chess signal — the real
  * model is a private artifact). Proves the wiring — session, batched leaf evaluation, chance node — runs end to end
  * and returns a legal turn; strength is measured in the arena, not here.
  */
class OnnxExpectimaxSearchSpec extends FunSuite:

  private val modelPath = getClass.getResource("/synthetic_test_model.onnx").getPath

  private def withBot[A](config: ExpectimaxConfig = ExpectimaxConfig())(f: OnnxExpectimaxSearch => A): A =
    val bot = new OnnxExpectimaxSearch(modelPath, config)
    try f(bot)
    finally bot.close()

  private val state =
    FenParser.parse(FenParser.InitialPosition).toOption.get.withDicePool(List(1, 1, 4))

  test("returns a legal turn from the starting position"):
    withBot()(bot => assert(bot.findBestMove(state).isDefined))

  test("honours a deadline and still returns a legal turn"):
    withBot()(bot => assert(bot.findBestMove(state, System.nanoTime(), Random(0)).isDefined))

  test("respects candidateLimit without error"):
    withBot(ExpectimaxConfig(candidateLimit = 2))(bot => assert(bot.findBestMove(state).isDefined))

  test("seeded findBestMove(state, random) returns a legal turn"):
    withBot()(bot => assert(bot.findBestMove(state, Random(0)).isDefined))
