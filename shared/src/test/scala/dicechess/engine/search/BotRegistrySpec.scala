package dicechess.engine.search

import munit.FunSuite

class BotRegistrySpec extends FunSuite:

  test("availableBots returns a list of configured bots sorted by difficulty") {
    val bots = BotRegistry.availableBots

    assertEquals(bots.size, 6)
    assertEquals(bots.head.id, "random")
    assertEquals(bots.head.difficulty, 1)

    assertEquals(bots(1).id, "checkmate-aware")
    assertEquals(bots(1).difficulty, 2)

    assertEquals(bots(2).id, "greedy")
    assertEquals(bots(2).difficulty, 3)

    assertEquals(bots(3).id, "greedy-v2")
    assertEquals(bots(3).difficulty, 4)

    assertEquals(bots(4).id, "aggressive")
    assertEquals(bots(4).difficulty, 5)

    assertEquals(bots(5).id, "prudent")
    assertEquals(bots(5).difficulty, 6)
  }

  test("getAlgorithm returns the correct algorithm for a given id (case-insensitive)") {
    assertEquals(BotRegistry.getAlgorithm("random"), Some(RandomSearch))
    assertEquals(BotRegistry.getAlgorithm("RANDOM"), Some(RandomSearch))

    assertEquals(BotRegistry.getAlgorithm("checkmate-aware"), Some(CheckmateAwareSearch))
    assertEquals(BotRegistry.getAlgorithm("CHECKMATE-AWARE"), Some(CheckmateAwareSearch))

    assertEquals(BotRegistry.getAlgorithm("greedy"), Some(GreedySearch))
    assertEquals(BotRegistry.getAlgorithm("GrEeDy"), Some(GreedySearch))

    assertEquals(BotRegistry.getAlgorithm("greedy-v2"), Some(GreedySearchV2))

    assertEquals(BotRegistry.getAlgorithm("aggressive"), Some(AggressiveSearch))
    assertEquals(BotRegistry.getAlgorithm("AGGRESSIVE"), Some(AggressiveSearch))

    assertEquals(BotRegistry.getAlgorithm("prudent"), Some(PrudentSearch))
    assertEquals(BotRegistry.getAlgorithm("PRUDENT"), Some(PrudentSearch))

    assertEquals(BotRegistry.getAlgorithm("unknown"), None)
    assertEquals(BotRegistry.getAlgorithm(null), None)
  }
  test("defaultAlgorithm returns GreedySearch") {
    assertEquals(BotRegistry.defaultAlgorithm, GreedySearch)
  }
