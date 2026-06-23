package dicechess.engine.cli

import munit.FunSuite

class CommandsSpec extends FunSuite:

  test("Commands.rootCommand parses 'eval' with ASCII flag correctly") {
    val args   = List("eval", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", "w", "KQkq", "-", "0", "1")
    val result = Commands.rootCommand.parse(args)
    assert(result.isRight)
    val cmd = result.toOption.get
    assert(cmd.isInstanceOf[EvalCommand])
    val evalCmd = cmd.asInstanceOf[EvalCommand]
    assertEquals(evalCmd.fen, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    assert(!evalCmd.unicode)
  }

  test("Commands.rootCommand parses 'eval --unicode' correctly") {
    val args   = List("eval", "--unicode", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", "w", "KQkq", "-", "0", "1")
    val result = Commands.rootCommand.parse(args)
    assert(result.isRight)
    val evalCmd = result.toOption.get.asInstanceOf[EvalCommand]
    assert(evalCmd.unicode)
  }

  test("Commands.rootCommand parses 'arena' correctly") {
    val args   = List("arena", "greedy", "random", "--games", "10")
    val result = Commands.rootCommand.parse(args)
    assert(result.isRight)
    val arenaCmd = result.toOption.get.asInstanceOf[ArenaCommand]
    assertEquals(arenaCmd.base, "greedy")
    assertEquals(arenaCmd.opponent, "random")
    assertEquals(arenaCmd.games, 10)
    assertEquals(arenaCmd.fen, None)
  }

  test("Commands.rootCommand parses 'arena' with trailing FEN correctly") {
    val args = List(
      "arena",
      "greedy",
      "random",
      "--games",
      "10",
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
      "w",
      "KQkq",
      "-",
      "0",
      "1"
    )
    val result = Commands.rootCommand.parse(args)
    assert(result.isRight)
    val arenaCmd = result.toOption.get.asInstanceOf[ArenaCommand]
    assertEquals(arenaCmd.fen, Some("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
  }

  test("Commands.rootCommand rejects unknown subcommand") {
    val args   = List("unknown")
    val result = Commands.rootCommand.parse(args)
    assert(result.isLeft)
  }

  test("Commands.rootCommand rejects invalid options for arena") {
    val args   = List("arena", "greedy", "random", "--games", "invalid_number")
    val result = Commands.rootCommand.parse(args)
    assert(result.isLeft)
  }

  test("Commands.execute runs EvalCommand correctly (smoke test)") {
    // We only smoke-test that execute doesn't crash since it prints to stdout
    val cmd = EvalCommand("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", unicode = false)
    Commands.execute(cmd)
  }

  test("Commands.execute handles invalid FEN gracefully in EvalCommand") {
    val cmd = EvalCommand("invalid_fen", unicode = false)
    Commands.execute(cmd)
  }

  test("Commands.execute runs ArenaCommand correctly (smoke test)") {
    // 0 games to make it return fast
    val cmd = ArenaCommand("greedy", "random", 0, None)
    Commands.execute(cmd)
  }

  test("Commands.execute handles ArenaCommand exception gracefully") {
    // using invalid FEN should trigger IllegalArgumentException and be caught
    val cmd = ArenaCommand("greedy", "random", 1, Some("invalid_fen"))
    Commands.execute(cmd)
  }
