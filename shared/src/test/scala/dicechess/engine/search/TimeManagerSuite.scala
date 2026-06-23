package dicechess.engine.search

import munit.FunSuite

class TimeManagerSuite extends FunSuite:

  // ---- Exact, hand-computed budgets (golden table). Pure math, no wall-clock. ----
  // Columns: label, clock, expected target, expected hard cap.
  private val cases: List[(String, ClockState, Long, Long)] = List(
    // Sudden death 1+0, move 1: reserve 3000, spendable 57000, mtg 29 -> 57000/29 = 1965; cap 12000.
    ("sudden-death 1+0 @ move 1", ClockState(60000, 0, 1), 1965L, 12000L),
    // Fischer 10+10, move 1: reserve 30000, spendable 570000, mtg 29 -> 10000 + 19655 = 29655; cap 120000.
    ("fischer 10+10 @ move 1", ClockState(600000, 10000, 1), 29655L, 120000L),
    // Sudden death late: reserve 300, spendable 3700, mtg floored at 12 -> 3700/12 = 308; cap 800.
    ("sudden-death late @ move 40", ClockState(4000, 0, 40), 308L, 800L),
    // Panic with a big increment: capped to 400 by hardCap, then clamped to PanicBudgetMs 200.
    ("panic with increment", ClockState(2000, 10000, 40), 200L, 400L),
    // Empty clock: everything floors to MinThinkMs.
    ("empty clock", ClockState(0, 0, 1), 20L, 20L),
    // Explicit movesToGo overrides the taper: 57000/10 = 5700.
    ("explicit movesToGo=10", ClockState(60000, 0, 1, Some(10)), 5700L, 12000L),
    // movesToGo=0 must not divide by zero: clamped to 1, so target hits the hardCap 12000.
    ("movesToGo=0 is division-safe", ClockState(60000, 0, 1, Some(0)), 12000L, 12000L)
  )

  cases.foreach { case (label, clock, expectedTarget, expectedCap) =>
    test(s"budget: $label") {
      val b = TimeManager.budget(clock)
      assertEquals(b.targetMs, expectedTarget, s"target for $clock")
      assertEquals(b.hardCapMs, expectedCap, s"hardCap for $clock")
    }
  }

  test("budgetMs subtracts the overhead buffer and floors at MinThinkMs") {
    // target 1965, buffer 150 -> 1815.
    assertEquals(TimeManager.budgetMs(ClockState(60000, 0, 1), 150L), 1815L)
    // target 29655, buffer 150 -> 29505.
    assertEquals(TimeManager.budgetMs(ClockState(600000, 10000, 1), 150L), 29505L)
    // target 20, buffer 150 -> floored to MinThinkMs (20), never negative.
    assertEquals(TimeManager.budgetMs(ClockState(0, 0, 1), 150L), TimeManager.MinThinkMs)
  }

  test("movesToGo: tapers with move number and floors at MinMovesToGo") {
    assertEquals(TimeManager.movesToGo(ClockState(60000, 0, 1)), 29)                        // 30 - 1
    assertEquals(TimeManager.movesToGo(ClockState(60000, 0, 25)), TimeManager.MinMovesToGo) // 30 - 25 < 12
    assertEquals(TimeManager.movesToGo(ClockState(60000, 0, 100)), TimeManager.MinMovesToGo)
    assertEquals(TimeManager.movesToGo(ClockState(60000, 0, 1, Some(7))), 7)
    assertEquals(TimeManager.movesToGo(ClockState(60000, 0, 1, Some(0))), 1) // division-safe clamp
  }

  // ---- Invariants (properties that must hold for every reasonable clock) ----

  private val sampleClocks: List[ClockState] =
    for
      remaining <- List(0L, 500L, 2000L, 5000L, 60000L, 180000L, 600000L)
      increment <- List(0L, 2000L, 10000L)
      move      <- List(1, 10, 30, 80)
    yield ClockState(remaining, increment, move)

  test("invariant: target is always within [MinThinkMs, hardCap]") {
    sampleClocks.foreach { c =>
      val b = TimeManager.budget(c)
      assert(b.targetMs >= TimeManager.MinThinkMs, s"target ${b.targetMs} below MinThink for $c")
      assert(b.targetMs <= b.hardCapMs, s"target ${b.targetMs} exceeds hardCap ${b.hardCapMs} for $c")
    }
  }

  test("invariant: never aims to spend more than the clock holds (no self-flag) above the panic floor") {
    // Once remaining comfortably exceeds MinThinkMs, the hard cap (a fraction of remaining) keeps the
    // target strictly below the clock, so a single turn can never flag.
    sampleClocks.filter(_.remainingMs >= 1000L).foreach { c =>
      val b = TimeManager.budget(c)
      assert(b.targetMs < c.remainingMs, s"target ${b.targetMs} >= remaining ${c.remainingMs} for $c")
    }
  }

  test("invariant: more remaining time never decreases the target (monotonic)") {
    val increasing = List(2000L, 5000L, 60000L, 180000L, 600000L)
    increasing.sliding(2).foreach {
      case List(lo, hi) =>
        val tLo = TimeManager.budget(ClockState(lo, 0, 10)).targetMs
        val tHi = TimeManager.budget(ClockState(hi, 0, 10)).targetMs
        assert(tHi >= tLo, s"target at $hi ($tHi) < target at $lo ($tLo)")
      case _ => ()
    }
  }

  test("invariant: a Fischer increment never lowers the target versus sudden death") {
    sampleClocks.foreach { c =>
      val suddenDeath = TimeManager.budget(c.copy(incrementMs = 0)).targetMs
      val withInc     = TimeManager.budget(c).targetMs
      assert(withInc >= suddenDeath, s"increment lowered target for $c ($withInc < $suddenDeath)")
    }
  }
