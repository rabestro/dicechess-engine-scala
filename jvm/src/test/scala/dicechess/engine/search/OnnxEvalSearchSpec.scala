package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite

/** Exercises the ONNX plumbing (feature extraction -> tensor -> session -> parsed output) against a tiny synthetic
  * model trained on random noise — it has no chess signal whatsoever, and is not meant to. A real, trained model is a
  * private artifact kept in a separate repository, not published alongside this codebase; this fixture only proves the
  * wiring is correct.
  */
class OnnxEvalSearchSpec extends FunSuite:

  private val modelPath =
    getClass.getResource("/synthetic_test_model.onnx").getPath

  test("onnxEval runs without throwing and returns a score on the scaled [0, 10000] axis") {
    val bot   = new OnnxEvalSearch(modelPath)
    val state = FenParser.parse(FenParser.InitialPosition).toOption.get
    try
      val score = bot.onnxEval(state, Color.White)
      assert(score >= 0 && score <= 10000, s"expected a score in [0, 10000], got $score")
    finally bot.close()
  }

  test("onnxEval is symmetric for a material-balanced position regardless of color") {
    // At the starting position, White's own material equals Black's own material, so
    // OnnxFeatures.extract(state, White) and OnnxFeatures.extract(state, Black) must be
    // identical feature vectors — the same invariant holds regardless of what the model itself
    // learned, since it's a property of the mover-perspective feature extraction, not the model.
    val bot   = new OnnxEvalSearch(modelPath)
    val state = FenParser.parse(FenParser.InitialPosition).toOption.get
    try assertEquals(bot.onnxEval(state, Color.White), bot.onnxEval(state, Color.Black))
    finally bot.close()
  }

  test("findBestMove returns a legal move from the starting position") {
    val bot   = new OnnxEvalSearch(modelPath)
    val start = FenParser.parse(FenParser.InitialPosition).toOption.get
    val state = start.copy(flags = start.flags.withDicePool(List(1, 1, 4)))
    try
      val result = bot.findBestMove(state, scala.util.Random(0))
      assert(result.isDefined)
    finally bot.close()
  }
