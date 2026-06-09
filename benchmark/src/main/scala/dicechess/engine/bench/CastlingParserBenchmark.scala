package dicechess.engine.bench

import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
class CastlingParserBenchmark:

  @Param(Array("-", "KQ", "KQkq", "kq"))
  var castling: String = uninitialized

  @Benchmark
  def parseContains(): Int = {
    var castlingInt = 0
    if castling.contains('K') then castlingInt |= 1
    if castling.contains('Q') then castlingInt |= 2
    if castling.contains('k') then castlingInt |= 4
    if castling.contains('q') then castlingInt |= 8
    castlingInt
  }

  @Benchmark
  def parseWhileLoop(): Int = {
    var castlingInt = 0
    var i           = 0
    while i < castling.length do
      castling.charAt(i) match
        case 'K' => castlingInt |= 1
        case 'Q' => castlingInt |= 2
        case 'k' => castlingInt |= 4
        case 'q' => castlingInt |= 8
        case _   =>
      i += 1
    castlingInt
  }

  @Benchmark
  def parseForeach(): Int = {
    var castlingInt = 0
    castling.foreach {
      case 'K' => castlingInt |= 1
      case 'Q' => castlingInt |= 2
      case 'k' => castlingInt |= 4
      case 'q' => castlingInt |= 8
      case _   =>
    }
    castlingInt
  }
