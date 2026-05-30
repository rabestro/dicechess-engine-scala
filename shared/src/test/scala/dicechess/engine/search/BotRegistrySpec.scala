package dicechess.engine.search

import munit.FunSuite

class BotRegistrySpec extends FunSuite:

  test("availableBots returns a list of configured bots sorted by difficulty") {
    val bots = BotRegistry.availableBots

    assertEquals(bots.size, 3)
    assertEquals(bots.head.id, "random")
    assertEquals(bots.head.difficulty, 1)

    assertEquals(bots(1).id, "greedy")
    assertEquals(bots(1).difficulty, 3)

    assertEquals(bots(2).id, "greedy-v2")
    assertEquals(bots(2).difficulty, 4)
  }

  test("getAlgorithm returns the correct algorithm for a given id (case-insensitive)") {
    assertEquals(BotRegistry.getAlgorithm("random"), Some(RandomSearch))
    assertEquals(BotRegistry.getAlgorithm("RANDOM"), Some(RandomSearch))

    assertEquals(BotRegistry.getAlgorithm("greedy"), Some(GreedySearch))
    assertEquals(BotRegistry.getAlgorithm("GrEeDy"), Some(GreedySearch))

    assertEquals(BotRegistry.getAlgorithm("greedy-v2"), Some(GreedySearchV2))

    assertEquals(BotRegistry.getAlgorithm("unknown"), None)
    assertEquals(BotRegistry.getAlgorithm(null), None)
  }
  test("defaultAlgorithm returns GreedySearch") {
    assertEquals(BotRegistry.defaultAlgorithm, GreedySearch)
  }
