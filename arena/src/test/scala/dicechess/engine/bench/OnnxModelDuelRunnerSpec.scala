package dicechess.engine.bench

import munit.FunSuite

/** Argument handling and end-to-end wiring for [[OnnxModelDuelRunner]], against the throwaway synthetic model shared
  * from rootJVM's test resources (no chess signal — the real models are private artifacts). Both sides play the same
  * file on purpose: this proves the duel runs, not which model is better, and strength is measured in a real arena.
  *
  * The rejection cases matter more than they look. Each one is an argument that would otherwise be discovered *after*
  * two onnxruntime sessions had been opened, or worse, silently accepted: a mistyped `--baseline-features` that fell
  * through to the wrong extractor would feed a model the wrong-shaped vector and quietly measure nonsense.
  */
class OnnxModelDuelRunnerSpec extends FunSuite:

  private val model = getClass.getResource("/synthetic_test_model.onnx").getPath

  private def run(args: String*) =
    ArenaOptions.parseAndRun(OnnxModelDuelRunner.command, args.toArray)

  test("requires a model on each side"):
    assert(run().isLeft)
    assert(run(model).isLeft)

  test("rejects an unknown feature set on either side"):
    assert(run(model, model, "--features", "bogus").isLeft)
    assert(run(model, model, "--baseline-features", "bogus").isLeft)

  test("rejects a non-positive candidate limit or game count"):
    assert(run(model, model, "--candidate-limit", "0").isLeft)
    assert(run(model, model, "--games", "0").isLeft)

  test("rejects an invalid time control before loading either model"):
    assert(run(model, model, "--presets", "0+2").isLeft)
    assert(run(model, model, "--presets", "").isLeft)

  test("plays a duel and writes a JSON report"):
    val out    = java.nio.file.Files.createTempFile("onnx-model-duel", ".json")
    val result = run(
      model,
      model,
      "--features",
      "material",
      "--baseline-features",
      "material",
      "--games",
      "1",
      "--candidate-limit",
      "2",
      "--presets",
      "1+0",
      "--seed",
      "7",
      "--json",
      out.toString
    )
    assert(result.isRight, result)
    assert(java.nio.file.Files.size(out) > 0L)
    java.nio.file.Files.deleteIfExists(out)

  test("registers both sides under distinct ids"):
    assertNotEquals(OnnxModelDuelRunner.ChallengerId, OnnxModelDuelRunner.DefenderId)
