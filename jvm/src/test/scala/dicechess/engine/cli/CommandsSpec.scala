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
