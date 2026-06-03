package dicechess.engine

/** Interactive Command Line Interface (CLI) for the Dice Chess Engine.
  *
  * This package implements the terminal REPL interface. It parses command arguments using `decline` and manages line
  * editing and command auto-completion using `jline`.
  *
  * ## Key Components
  *
  *   - [[dicechess.engine.cli.Commands]]: Parser definitions, command execution mapping, and help system.
  *   - [[dicechess.engine.cli.BoardPrinter]]: Formatter that renders board states using ASCII or Unicode chess glyphs.
  */
package object cli
