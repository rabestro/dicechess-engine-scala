package dicechess

/** Core library and entry points for the Dice Chess Engine.
  *
  * The Dice Chess Engine is a high-performance game engine written in Scala 3, cross-compiled to JVM and JavaScript
  * targets.
  *
  * ## Package Structure
  *
  *   - [[dicechess.engine.domain]]: Fundamental domain types, board representations, opaque wrappers, and FEN parsers.
  *   - [[dicechess.engine.movegen]]: High-performance bitboard move generators (pawns, leapers, magic sliders).
  *   - [[dicechess.engine.search]]: Turn path generation, AI search algorithms (Greedy, Prudent), and evaluation.
  *   - [[dicechess.engine.cli]]: Interactive command-line REPL interpreter (JVM).
  *   - [[dicechess.engine.bench]]: Bot battle arena match simulation framework (JVM).
  *   - [[dicechess.engine.api]]: JavaScript API facade and wrapper classes (JS).
  */
package object engine
