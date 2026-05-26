package dicechess.engine.domain

import munit.FunSuite

class GameFlagsSpec extends FunSuite {

  test("GameFlags empty initialization") {
    val flags = GameFlags.empty
    assertEquals(flags.activeColor, Color.White)
    assertEquals(flags.castlingRights, 0)
    assertEquals(flags.enPassantFiles, 0)
    assertEquals(flags.dicePool, Nil)
    assertEquals(flags.halfMoveClock, 0)
  }

  test("GameFlags pack and unpack all fields") {
    val flags = GameFlags(
      color = Color.Black,
      castlingRights = 10,  // 1010
      enPassantFiles = 129, // 10000001
      dice1 = 2,
      dice2 = 4,
      dice3 = 6,
      halfMoveClock = 85
    )

    assertEquals(flags.activeColor, Color.Black)
    assertEquals(flags.castlingRights, 10)
    assertEquals(flags.enPassantFiles, 129)
    assertEquals(flags.dicePool, List(2, 4, 6))
    assertEquals(flags.halfMoveClock, 85)
  }

  test("GameFlags modifiers should work independently") {
    val initial = GameFlags.empty

    val step1 = initial.toggleActiveColor
    assertEquals(step1.activeColor, Color.Black)
    assertEquals(step1.castlingRights, 0) // Unchanged

    val step2 = step1.withCastlingRights(15) // 1111
    assertEquals(step2.activeColor, Color.Black)
    assertEquals(step2.castlingRights, 15)
    assertEquals(step2.halfMoveClock, 0)

    val step3 = step2.withEnPassantFiles(255)
    assertEquals(step3.castlingRights, 15)
    assertEquals(step3.enPassantFiles, 255)

    val step4 = step3.withHalfMoveClock(100)
    assertEquals(step4.enPassantFiles, 255)
    assertEquals(step4.halfMoveClock, 100)

    val step5 = step4.addDie(3)
    assertEquals(step5.dicePool, List(3))
    assertEquals(step5.halfMoveClock, 100)
  }

  test("GameFlags fromList") {
    val flags = GameFlags.fromList(
      color = Color.White,
      castlingRights = 0,
      enPassantFiles = 0,
      dicePool = List(1, 5),
      halfMoveClock = 0
    )

    assertEquals(flags.dicePool, List(1, 5))
  }

  test("GameFlags dice management") {
    var flags = GameFlags.empty
    assertEquals(flags.dicePool, Nil)

    flags = flags.addDie(6)
    assertEquals(flags.dicePool, List(6))

    flags = flags.addDie(2)
    assertEquals(flags.dicePool, List(6, 2))

    flags = flags.addDie(2)
    assertEquals(flags.dicePool, List(6, 2, 2))

    intercept[IllegalArgumentException] {
      flags.addDie(7)
    }

    intercept[IllegalStateException] {
      flags.addDie(1)
    }

    flags = flags.removeDie(2)
    assertEquals(flags.dicePool, List(6, 2))

    flags = flags.removeDie(5) // Not present
    assertEquals(flags.dicePool, List(6, 2))

    flags = flags.removeDie(6)
    assertEquals(flags.dicePool, List(2))

    flags = flags.removeDie(2)
    assertEquals(flags.dicePool, Nil)
  }
}
