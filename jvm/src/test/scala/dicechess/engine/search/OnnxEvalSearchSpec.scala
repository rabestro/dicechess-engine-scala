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

  // A few materially-distinct positions so the batch has real variation to reproduce.
  private val fens = List(
    FenParser.InitialPosition,
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1", // White missing a queen
    "r1bqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"  // Black missing a knight
  )

  test("onnxEvalBatch matches evaluating each position individually, in order") {
    val bot    = new OnnxEvalSearch(modelPath)
    val states = fens.map(fen => FenParser.parse(fen).toOption.get).toArray
    try
      val batched    = bot.onnxEvalBatch(states, Color.White)
      val individual = states.map(bot.onnxEval(_, Color.White))
      assertEquals(batched.toList, individual.toList)
    finally bot.close()
  }

  test("onnxEvalBatch on an empty input yields an empty result") {
    val bot = new OnnxEvalSearch(modelPath)
    try assertEquals(bot.onnxEvalBatch(Array.empty, Color.White).toList, Nil)
    finally bot.close()
  }

  test("onnxEval feeds the supplied extractor's output to the model (9-wide RichFeatures is rejected here)") {
    // Proves the constructor's extractor actually drives the tensor: RichFeatures' 9 columns can't
    // fit this 7-feature model, so the session run must fail. The default (OnnxFeatures, 7) is
    // exercised by every other test.
    val bot   = new OnnxEvalSearch(modelPath, RichFeatures.extract)
    val state = FenParser.parse(FenParser.InitialPosition).toOption.get
    try intercept[Exception](bot.onnxEval(state, Color.White))
    finally bot.close()
  }

  test("onnxEvalBatch also routes through the supplied extractor (9-wide RichFeatures is rejected here)") {
    val bot    = new OnnxEvalSearch(modelPath, RichFeatures.extract)
    val states = Array(FenParser.parse(FenParser.InitialPosition).toOption.get)
    try intercept[Exception](bot.onnxEvalBatch(states, Color.White))
    finally bot.close()
  }
