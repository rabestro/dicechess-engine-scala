package dicechess.engine.bench

import java.nio.file.{Files, Path}
import java.util.zip.GZIPInputStream

import scala.io.Source
import scala.util.{Random, Using}

import dicechess.engine.domain.*
import dicechess.engine.search.*

/** Measures how much a better root pre-ranker could possibly be worth, before anyone builds one.
  *
  * The search pre-ranks every legal turn — by raw material, by default — and expands only the top `candidateLimit`.
  * Production keeps 24 of a median 132, so the pre-ranker decides what the search is even allowed to consider. The open
  * question (`dicechess-ev#14`) is whether that cut throws away moves that matter.
  *
  * This answers it without a trained ranker: run the SAME evaluator twice over the same position, once at the
  * production limit and once full width, and count how often the decision changes. Because the wide run's candidate set
  * is a superset of the narrow one's, a disagreement means exactly one thing — the material cut hid a turn the search
  * preferred. `P(disagree)` is therefore an upper bound on what any pre-ranker improvement can recover at that limit,
  * and the value gap says whether the hidden turns were meaningfully better or ties.
  *
  * Deliberately an upper bound and not an estimate of the gain: a perfect pre-ranker would recover all of it, and a
  * realistic one much less. If this number is small, `dicechess-ev#14` is a null before any data is generated.
  *
  * Untimed on purpose. The question is about the candidate cut, not the clock — mixing in a deadline would confound
  * "the pre-ranker hid the move" with "the search ran out of time", and telemetry already showed production is not
  * clock-starved (median 24 of 24 candidates completed).
  *
  * Usage: `runMain dicechess.engine.bench.PreRankRecallProbeMain <model.onnx> <corpus.csv.gz> [positions] [limit]
  * [features] [seed]`, where the corpus is an `ExportTrainingDataApp` CSV (`fen` and `dice` columns) and `limit` is the
  * production candidate limit under test (default 24).
  */
object PreRankRecallProbeMain:

  /** Stands in for "no cut at all". Larger than any legal turn count Dice Chess can produce (the observed maximum is
    * ~11.7k), so the wide run genuinely ranks every candidate.
    */
  private val NoLimit = 1_000_000

  final private case class Decision(legalTurns: Int, agreed: Boolean, narrowScore: Int, wideScore: Int)

  def main(args: Array[String]): Unit =
    val modelPath = args.headOption.getOrElse(
      sys.error(
        "Usage: PreRankRecallProbeMain <model.onnx> <corpus.csv.gz> [positions] [limit] [features] [seed]"
      )
    )
    val corpusPath = args.lift(1).getOrElse(sys.error("corpus CSV path is required"))
    val positions  = args.lift(2).flatMap(_.toIntOption).getOrElse(300)
    val limit      = args.lift(3).flatMap(_.toIntOption).getOrElse(24)
    val featureSet = args.lift(4).getOrElse("rich")
    val seed       = args.lift(5).flatMap(_.toLongOption).getOrElse(42L)

    val extract: (GameState, Color) => Array[Float] = featureSet.toLowerCase match
      case "material" => OnnxFeatures.extract
      case "rich"     => RichFeatures.extract
      case "kcp"      => KcpFeatures.extract
      case "rawboard" => RawBoardFeatures.extract
      case other      => sys.error(s"Unknown feature set '$other'")

    val sampled = samplePositions(corpusPath, positions, seed)
    println(s"Probing $limit vs full width on ${sampled.size} positions ($featureSet, $modelPath)")

    val narrow = new OnnxExpectimaxSearch(modelPath, ExpectimaxConfig(limit), extract)
    val wide   = new OnnxExpectimaxSearch(modelPath, ExpectimaxConfig(NoLimit), extract)
    try
      val decisions = sampled.zipWithIndex.flatMap { case (state, i) =>
        if (i + 1) % 25 == 0 then println(s"  ${i + 1}/${sampled.size}")
        probe(state, narrow, wide)
      }
      report(decisions, limit)
    finally
      narrow.close()
      wide.close()

  /** One position: does the cut change the decision? `None` when the position has nothing to decide. */
  private def probe(state: GameState, narrow: SearchAlgorithm, wide: SearchAlgorithm): Option[Decision] =
    val legalTurns = TurnGenerator.generateAllLegalTurnPaths(state).size
    if legalTurns <= 1 then None
    else
      // Same seed on both sides so a tie broken at random cannot masquerade as a disagreement. Compared as UCI
      // text rather than as move objects: `strictEquality` has no CanEqual for List[Move], and the rendered turn is
      // what a human reads out of a disagreement anyway.
      for
        n <- narrow.findBestMove(state, new Random(7))
        w <- wide.findBestMove(state, new Random(7))
      yield
        val narrowTurn = n.moves.map(_.toUci).mkString(" ")
        val wideTurn   = w.moves.map(_.toUci).mkString(" ")
        Decision(legalTurns, narrowTurn == wideTurn, n.score, w.score)

  private def report(decisions: List[Decision], limit: Int): Unit =
    val n = decisions.size
    if n == 0 then println("No decidable positions sampled — nothing to report.")
    else
      val disagreed = decisions.filterNot(_.agreed)
      // Only positions with MORE legal turns than the limit can possibly be affected; quoting the rate over all
      // sampled positions would dilute it with decisions the cut never touched.
      val exposed    = decisions.filter(_.legalTurns > limit)
      val exposedDis = exposed.filterNot(_.agreed)
      // A changed decision with an identical value is a TIE broken differently, not value the cut hid. Counting it as
      // upside would overstate what a better pre-ranker can win, so the two are reported apart.
      val gaps                        = disagreed.map(d => d.wideScore - d.narrowScore)
      val realGain                    = gaps.filter(_ > 0).sorted
      val ties                        = gaps.count(_ == 0)
      val exposedGain                 = exposed.filterNot(_.agreed).count(d => d.wideScore - d.narrowScore > 0)
      val turns                       = decisions.map(_.legalTurns).sorted
      val medianTurns                 = turns(n / 2)
      def pct(a: Int, b: Int): String = if b == 0 then "n/a" else f"${100.0 * a / b}%.1f%%"
      println(s"""
                 |=== Pre-rank recall probe: candidateLimit=$limit vs full width ===
                 |positions with a real choice   : $n  (median legal turns $medianTurns)
                 |  of those, exposed to the cut : ${exposed.size}  (legal turns > $limit)
                 |legal turns                    : p10 ${turns(n / 10)}  p50 $medianTurns  p90 ${turns(9 * n / 10)}
                 |decision changed               : ${disagreed.size} / $n = ${pct(disagreed.size, n)}
                 |  among exposed positions      : ${exposedDis.size} / ${exposed.size} = ${pct(
                  exposedDis.size,
                  exposed.size
                )}
                 |  of those, mere ties          : $ties  (same value, different turn — no upside)
                 |  with a real value gain       : ${realGain.size}
                 |
                 |Upper bound on what a better pre-ranker at limit=$limit can recover:
                 |  ${pct(exposedGain, exposed.size)} of exposed decisions carry a genuine value gain.
                 |""".stripMargin)
      if realGain.nonEmpty then
        println(
          f"value gain when the cut hid something better (model score units, leaf scale ~10000): " +
            f"p10 ${realGain(realGain.size / 10)} p50 ${realGain(realGain.size / 2)} " +
            f"p90 ${realGain(9 * realGain.size / 10)} max ${realGain.last}"
        )

  /** Reservoir-free sample: read every `fen`/`dice` row, keep a seeded random subset of the parsed states.
    *
    * Reads the whole file rather than the first N rows on purpose — the corpus is ordered by game, so a prefix would be
    * a handful of games rather than a spread of positions, and opening positions have far fewer legal turns than
    * midgame ones.
    */
  private def samplePositions(corpusPath: String, wanted: Int, seed: Long): List[GameState] =
    val path = Path.of(corpusPath)
    if !Files.exists(path) then sys.error(s"corpus not found: $corpusPath")
    val rows = Using.resource(Source.fromInputStream(new GZIPInputStream(Files.newInputStream(path)))) { src =>
      val lines  = src.getLines()
      val header = lines.next().split(',').toList
      val fenAt  = header.indexOf("fen")
      val diceAt = header.indexOf("dice")
      if fenAt < 0 || diceAt < 0 then sys.error(s"corpus must have 'fen' and 'dice' columns, got: $header")
      lines.map { line =>
        val cells = line.split(',')
        (cells(fenAt), cells(diceAt))
      }.toVector
    }
    val picked = new Random(seed).shuffle(rows).iterator
    Iterator
      .continually(if picked.hasNext then Some(picked.next()) else None)
      .takeWhile(_.isDefined)
      .flatten
      .flatMap((fen, dice) => parseState(fen, dice))
      .take(wanted)
      .toList

  /** The corpus stores a bare FEN plus the roll in a separate column; the search needs both in one state. */
  private[bench] def parseState(fen: String, dice: String): Option[GameState] =
    val pool = dice.toUpperCase.flatMap {
      case 'P' => Some(PieceType.Pawn.diceValue)
      case 'N' => Some(PieceType.Knight.diceValue)
      case 'B' => Some(PieceType.Bishop.diceValue)
      case 'R' => Some(PieceType.Rook.diceValue)
      case 'Q' => Some(PieceType.Queen.diceValue)
      case 'K' => Some(PieceType.King.diceValue)
      case _   => None
    }.toList
    if pool.isEmpty then None
    else FenParser.parse(s"$fen 0 1").toOption.map(_.withDicePool(pool))
