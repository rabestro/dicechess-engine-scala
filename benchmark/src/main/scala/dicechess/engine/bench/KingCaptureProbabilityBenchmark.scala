package dicechess.engine.bench

import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit
import dicechess.engine.domain.*
import dicechess.engine.search.KingCaptureProbability

import scala.compiletime.uninitialized

/** Micro-benchmarks for [[KingCaptureProbability]] — the per-ply hot path of the Monte-Carlo bot.
  *
  * `MonteCarloEquity.singleRollout` calls [[KingCaptureProbability.kingCaptureProbability]] once on every ply of every
  * rollout, so KCP throughput is roughly proportional to the bot's playing strength under a fixed time budget. This
  * benchmark establishes the baseline for KCP optimizations; measure on defended-king mid/endgames (not a hanging king)
  * so a direct-capture fast-path cannot flatter the numbers.
  *
  * The result is wrapped in a [[DoubleHolder]] so JMH cannot dead-code-eliminate the computation.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
class KingCaptureProbabilityBenchmark:

  @Param(Array("initial", "kiwipete", "endgame"))
  var position: String = uninitialized

  @Param(Array("white", "black"))
  var defender: String = uninitialized

  var state: GameState     = uninitialized
  var defenderColor: Color = Color.White

  @Setup(Level.Trial)
  def setup(): Unit =
    state = BenchmarkPositions.parse(BenchmarkPositions.AllPositions(position))
    defenderColor = if defender == "white" then Color.White else Color.Black

  @Benchmark
  def kingCaptureProbability(): DoubleHolder =
    DoubleHolder(KingCaptureProbability.kingCaptureProbability(state, defenderColor))

  @Benchmark
  def queenCaptureProbability(): DoubleHolder =
    DoubleHolder(KingCaptureProbability.queenCaptureProbability(state, defenderColor))

/** Wraps a probability so JMH cannot dead-code-eliminate the computation. */
final case class DoubleHolder(value: Double)
