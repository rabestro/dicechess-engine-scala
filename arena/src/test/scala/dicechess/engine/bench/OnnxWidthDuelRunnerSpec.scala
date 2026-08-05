package dicechess.engine.bench

import munit.FunSuite

/** Pins [[OnnxWidthDuelRunner]]'s argument handling.
  *
  * The duel itself needs an ONNX session and real games, so what is worth testing here is everything that decides
  * *what* gets run: the defaults, the flags, and the two inputs that must be refused rather than silently burn a run.
  */
class OnnxWidthDuelRunnerSpec extends FunSuite:

  private def parse(args: String*) = OnnxWidthDuelRunner.parseArgs(args.toArray)

  test("a model path alone yields the documented defaults"):
    val a = parse("model.onnx")
    assertEquals(a.modelPath, "model.onnx")
    assertEquals(a.wideK, 48)
    assertEquals(a.narrowK, 24)
    assertEquals(a.featureSet, "rich")
    assertEquals(a.gamesPerColor, 10)
    assertEquals(a.presets, "3+2")
    assertEquals(a.seed, 42L)
    assertEquals(a.sprtConfig, None)
    assertEquals(a.jsonPath, None)

  test("every positional argument is honoured in order"):
    val a = parse("m.onnx", "64", "8", "kcp", "400", "1+0,5+3", "1000")
    assertEquals(a.wideK, 64)
    assertEquals(a.narrowK, 8)
    assertEquals(a.featureSet, "kcp")
    assertEquals(a.gamesPerColor, 400)
    assertEquals(a.presets, "1+0,5+3")
    assertEquals(a.seed, 1000L)

  test("--json and --sprt are extracted wherever they sit"):
    val a = parse("m.onnx", "--json", "out.json", "48", "24", "rich", "--sprt", "0,20,0.05,0.05")
    assertEquals(a.jsonPath, Some("out.json"))
    assertEquals(a.wideK, 48)
    assertEquals(a.narrowK, 24)
    assert(a.sprtConfig.isDefined)

  test("duelling a width against itself is refused"):
    // Otherwise the run would spend hours measuring nothing but its own noise, and the score would sit near 50%
    // for a reason that looks exactly like a genuine null result.
    val error = intercept[RuntimeException](parse("m.onnx", "24", "24"))
    assert(error.getMessage.contains("must be greater"), error.getMessage)

  test("a narrower wideK is refused rather than silently inverting the conclusion"):
    // Every label would stay self-consistent — "K=8 (bot under test) vs K=24" — while the sentence the report is read
    // with, "above 50% means widening helped", quietly became false. Swapping the arguments is the fix, not tolerance.
    val error = intercept[RuntimeException](parse("m.onnx", "8", "24"))
    assert(error.getMessage.contains("must be greater"), error.getMessage)

  test("a non-positive game count is refused"):
    assert(intercept[RuntimeException](parse("m.onnx", "48", "24", "rich", "0")).getMessage.contains("positive"))

  test("a malformed number is refused instead of falling back to its default"):
    // The dangerous case: `wideK=abc` used to run at 48 and produce a report that looked perfectly normal, which for
    // a measurement tool is worse than crashing — the number gets quoted later as if it answered the question asked.
    for (args, name) <- List(
        (Seq("m.onnx", "abc"), "wideK"),
        (Seq("m.onnx", "48", "xyz"), "narrowK"),
        (Seq("m.onnx", "48", "24", "rich", "lots"), "gamesPerColor"),
        (Seq("m.onnx", "48", "24", "rich", "10", "3+2", "later"), "seed")
      )
    do
      val error = intercept[RuntimeException](parse(args*))
      assert(error.getMessage.contains(name), s"$name: ${error.getMessage}")

  test("zero and negative widths are refused"):
    assert(intercept[RuntimeException](parse("m.onnx", "0")).getMessage.contains("positive"))
    assert(intercept[RuntimeException](parse("m.onnx", "48", "-1")).getMessage.contains("positive"))

  test("absent arguments still take their defaults"):
    // The rejections above must not have turned "not supplied" into an error.
    val a = parse("m.onnx")
    assertEquals((a.wideK, a.narrowK, a.gamesPerColor, a.seed), (48, 24, 10, 42L))

  test("a missing model path is refused with the usage line"):
    assert(intercept[RuntimeException](parse()).getMessage.contains("Usage"))

  test("each feature set maps to an extractor of its own width"):
    val start = dicechess.engine.domain.FenParser.parse(dicechess.engine.domain.FenParser.InitialPosition).toOption.get
    val white = dicechess.engine.domain.Color.White
    assertEquals(parse("m.onnx", "48", "24", "material").extractFeatures(start, white).length, 7)
    assertEquals(parse("m.onnx", "48", "24", "rich").extractFeatures(start, white).length, 9)
    assertEquals(parse("m.onnx", "48", "24", "kcp").extractFeatures(start, white).length, 13)
    assertEquals(parse("m.onnx", "48", "24", "rawboard").extractFeatures(start, white).length, 768)

  test("an unknown feature set fails fast rather than serving the wrong input width"):
    val error = intercept[RuntimeException](parse("m.onnx", "48", "24", "planes").extractFeatures)
    assert(error.getMessage.contains("Unknown feature set"), error.getMessage)
