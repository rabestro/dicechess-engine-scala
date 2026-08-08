package dicechess.engine.bench

import dicechess.engine.search.{BotInfo, BotRegistry, ExpectimaxConfig, OnnxExpectimaxSearch}

/** Makes an ONNX model playable by the arena, by giving it a [[BotRegistry]] id.
  *
  * [[BotMatchRunner]] resolves both sides of a match by registry id, and a model file is not a registry bot until
  * something registers it — so every ONNX runner that wants to reuse the engine's own match code has to perform this
  * step first. Keeping it here is what lets those runners differ only in what they measure.
  *
  * The returned search owns a native onnxruntime session and is [[AutoCloseable]]; the caller must close it, and
  * [[scala.util.Using]] is the intended way. Registration itself is not undone on close: [[BotRegistry]] is a
  * process-wide singleton keyed by id, so a later registration under the same id simply replaces this one.
  */
private[bench] object OnnxArenaBot:

  /** @param difficulty
    *   registry presentation metadata only — the arena never reads it when running a match.
    */
  def register(
      id: String,
      modelPath: String,
      featureSet: String,
      config: ExpectimaxConfig,
      difficulty: Int,
      description: String
  ): OnnxExpectimaxSearch =
    val bot = new OnnxExpectimaxSearch(modelPath, config, ArenaOptions.extractFeatures(featureSet))
    BotRegistry.registerCustomBot(
      BotInfo(
        id = id,
        name = s"ONNX Expectimax ($featureSet, K=${config.candidateLimit})",
        description = description,
        difficulty = difficulty,
        isExperimental = true
      ),
      bot
    )
    bot
