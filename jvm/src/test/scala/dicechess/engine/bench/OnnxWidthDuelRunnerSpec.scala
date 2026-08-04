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
    assert(error.getMessage.contains("must differ"), error.getMessage)

  test("a non-positive game count is refused"):
    assert(intercept[RuntimeException](parse("m.onnx", "48", "24", "rich", "0")).getMessage.contains("> 0"))

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
