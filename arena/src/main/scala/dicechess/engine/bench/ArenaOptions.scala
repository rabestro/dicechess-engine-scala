package dicechess.engine.bench

import cats.data.Validated

import com.monovore.decline.*
import dicechess.engine.search.ExpectimaxConfig

private[bench] object ArenaOptions:

  val modelPathOpt: Opts[String] =
    Opts.argument[String](metavar = "model.onnx")

  val rescoreModelPathOpt: Opts[Option[String]] =
    Opts.option[String]("rescore-model", help = "Path to the rescore ONNX model").orNone

  val rescoreWeightOpt: Opts[Double] =
    Opts.option[Double]("rescore-weight", help = "Weight for rescore model").withDefault(0.5)

  def baseBotOpt(default: String = "greedy"): Opts[String] =
    Opts.option[String]("base-bot", help = s"Baseline bot ID (default: $default)").withDefault(default)

  def opponentOpt(default: String = "aggressive"): Opts[String] =
    Opts.option[String]("opponent", help = s"Opponent bot ID (default: $default)").withDefault(default)

  def gamesOpt(default: Int = 50): Opts[Int] =
    Opts
      .option[Int]("games", help = s"Number of games per color (default: $default)")
      .withDefault(default)
      .validate("games must be > 0")(_ > 0)

  def seedOpt(default: Long = 42L): Opts[Long] =
    Opts.option[Long]("seed", help = s"Random seed (default: $default)").withDefault(default)

  val jsonPathOpt: Opts[Option[String]] =
    Opts.option[String]("json", help = "Path to write machine-readable JSON report").orNone

  val sprtConfigOpt: Opts[Option[SprtConfig]] =
    Opts
      .option[String]("sprt", help = "SPRT config as elo0,elo1,alpha,beta (e.g. -2,2,0.05,0.05)")
      .mapValidated { str =>
        val parts = str.split(",").map(_.trim)
        if parts.length == 4 then
          try
            val elo0  = parts(0).toDouble
            val elo1  = parts(1).toDouble
            val alpha = parts(2).toDouble
            val beta  = parts(3).toDouble
            if elo0 >= elo1 then Validated.invalidNel("SPRT config values must have elo0 < elo1")
            else if alpha <= 0.0 || alpha >= 1.0 then Validated.invalidNel("SPRT alpha must be strictly in (0.0, 1.0)")
            else if beta <= 0.0 || beta >= 1.0 then Validated.invalidNel("SPRT beta must be strictly in (0.0, 1.0)")
            else Validated.valid(SprtConfig(elo0, elo1, alpha, beta))
          catch case _: NumberFormatException => Validated.invalidNel("SPRT config values must be numbers")
        else Validated.invalidNel("SPRT config must be 4 comma-separated values: elo0,elo1,alpha,beta")
      }
      .orNone

  def featuresOpt(default: String = "rich"): Opts[String] =
    Opts
      .option[String]("features", help = s"Feature set: material, rich, kcp, rawboard (default: $default)")
      .withDefault(default)
      .validate("Unknown feature set") { set =>
        Set("material", "rich", "kcp", "rawboard").contains(set.toLowerCase)
      }

  def extractFeatures(
      featureSet: String
  ): (dicechess.engine.domain.GameState, dicechess.engine.domain.Color) => Array[Float] =
    featureSet.toLowerCase match
      case "material" => dicechess.engine.search.OnnxFeatures.extract
      case "rich"     => dicechess.engine.search.RichFeatures.extract
      case "kcp"      => dicechess.engine.search.KcpFeatures.extract
      case "rawboard" => dicechess.engine.search.RawBoardFeatures.extract
      case other      => sys.error(s"Unknown feature set '$other'")

  def candidateLimitOpt(default: Int = ExpectimaxConfig().candidateLimit): Opts[Int] =
    Opts
      .option[Int]("candidate-limit", help = s"Candidate limit for expectimax search (default: $default)")
      .withDefault(default)
      .validate("limit must be > 0")(_ > 0)

  val preRankWithModelOpt: Opts[Boolean] =
    Opts.flag("pre-rank-with-model", help = "Use rescore model for pre-ranking").orFalse

  val bookPathOpt: Opts[Option[String]] =
    Opts.option[String]("book", help = "Path to the opening book JSON file").orNone

  val reqBookPathOpt: Opts[String] =
    Opts.argument[String](metavar = "book.json")

  def presetsOpt(default: String = "1+0,3+2,10+10"): Opts[String] =
    Opts.option[String]("presets", help = s"Comma-separated time controls (default: $default)").withDefault(default)

  def wideKOpt(default: Int = 48): Opts[Int] =
    Opts
      .option[Int]("wide-k", help = s"Candidate limit for wide search (default: $default)")
      .withDefault(default)
      .validate("wide-k must be > 0")(_ > 0)

  def narrowKOpt(default: Int = 24): Opts[Int] =
    Opts
      .option[Int]("narrow-k", help = s"Candidate limit for narrow search (default: $default)")
      .withDefault(default)
      .validate("narrow-k must be > 0")(_ > 0)

  val corpusPathOpt: Opts[String] =
    Opts.argument[String](metavar = "corpus.csv.gz")

  def positionsOpt(default: Int = 300): Opts[Int] =
    Opts
      .option[Int]("positions", help = s"Number of positions to probe (default: $default)")
      .withDefault(default)
      .validate("positions must be > 0")(_ > 0)

  def limitOpt(default: Int = 24): Opts[Int] =
    Opts
      .option[Int]("limit", help = s"Production candidate limit under test (default: $default)")
      .withDefault(default)
      .validate("limit must be > 0")(_ > 0)
