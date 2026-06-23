package dicechess.engine.search

/** A snapshot of the moving side's game clock, in **milliseconds**.
  *
  * This is the input to time management — everything [[TimeManager]] needs to turn a game clock into a per-turn
  * thinking budget. The unit of time is a full Dice Chess *turn* (1–3 micro-moves searched together), and the Fischer
  * increment is credited per turn.
  *
  * @param remainingMs
  *   time left on the moving side's clock before this turn, in milliseconds
  * @param incrementMs
  *   per-turn Fischer increment in milliseconds (`0` for sudden death)
  * @param moveNumber
  *   the full-move number (the DFEN 6th field); used to taper the moves-to-go estimate
  * @param movesToGo
  *   explicit moves-to-go for a tournament-style control, when known; otherwise [[TimeManager]] estimates it
  */
final case class ClockState(
    remainingMs: Long,
    incrementMs: Long,
    moveNumber: Int,
    movesToGo: Option[Int] = None
)

/** The result of [[TimeManager.budget]]: the ideal per-turn target and the hard ceiling it was clamped to.
  *
  * @param targetMs
  *   the time the bot should aim to spend on this turn
  * @param hardCapMs
  *   the absolute per-turn ceiling (a fraction of the remaining clock) — the bot must never aim above this, so a single
  *   turn can never flag the clock
  */
final case class TimeBudget(targetMs: Long, hardCapMs: Long)

/** Pure, platform-agnostic time-management policy for the bots' game clock.
  *
  * This is **layer (b)** of time control: it maps a [[ClockState]] to a per-turn budget in milliseconds. **Layer (a)**
  * — the actual search under a deadline — stays untouched in [[TimeBudgetedSearch]]; the caller turns the budget
  * returned here into a `System.nanoTime` deadline. Keeping the policy here (rather than in each consumer) means the JS
  * API, the offline arena, and any future server share one tested implementation instead of re-inventing it.
  *
  * The policy is pure `Long`/`Double` math — no clocks, no randomness — so it is identical on the JVM and Scala.js and
  * is exhaustively unit-testable without wall-clock flakiness (see [[TimeBudgetedSearch]] for why the *search* path
  * cannot be).
  *
  * ## How the budget is derived
  *
  * From the spendable time (remaining minus a safety reserve) and an estimated moves-to-go:
  *
  * ```scala
  * reserve = max(ReserveFloorMs, ReserveFraction * remaining)
  * spendable = max(0, remaining - reserve)
  * target = increment + spendable / movesToGo // increment == 0 ⇒ sudden death
  * ```
  *
  * then clamped to `[MinThinkMs, hardCap]` where `hardCap = MaxFraction * remaining`, with a tighter panic clamp once
  * `spendable` falls below [[PanicThresholdMs]].
  *
  * ## Behaviour across controls
  *
  *   - **Sudden death (1+0):** target ≈ `spendable / movesToGo`, decaying as the clock drains; near zero the bot
  *     blitzes legal moves (panic floor) instead of flagging.
  *   - **Fischer (10+10):** the `increment` term keeps per-turn time high while the clock is healthy. As `remaining`
  *     shrinks the `hardCap` (a fraction of the remaining clock) takes over and throttles the target below the
  *     increment — deliberately conservative, since the increment is only credited *after* the turn completes, so a bot
  *     that overspends now can still flag before it is refunded. This trades a little time-utilisation for a guarantee
  *     of never losing on time; the constants are tunable.
  */
object TimeManager:

  /** Lower bound on the reserve kept untouched on the clock. */
  val ReserveFloorMs: Long = 300L

  /** Reserve kept as a fraction of the remaining clock (whichever is larger wins over [[ReserveFloorMs]]). */
  val ReserveFraction: Double = 0.05

  /** Moves-to-go estimate at move 1 when none is supplied; tapers down with the move number. */
  val BaseMovesToGo: Int = 30

  /** Floor on the estimated moves-to-go, so a long game never starves the per-turn budget. */
  val MinMovesToGo: Int = 12

  /** Hard per-turn ceiling as a fraction of the remaining clock — never bet more than this on one turn. */
  val MaxFraction: Double = 0.20

  /** Absolute floor on any budget, so the bot always gets a sliver of thinking time. */
  val MinThinkMs: Long = 20L

  /** Spendable-time level below which the panic clamp engages. */
  val PanicThresholdMs: Long = 2000L

  /** Per-turn budget cap while in panic, to stretch the last seconds over several turns. */
  val PanicBudgetMs: Long = 200L

  /** Estimated number of further turns the moving side must play, used to spread the spendable time.
    *
    * Honours an explicit [[ClockState.movesToGo]] when present (clamped to at least 1 to stay division-safe); otherwise
    * tapers from [[BaseMovesToGo]] by the move number, never below [[MinMovesToGo]].
    */
  def movesToGo(clock: ClockState): Int =
    val estimate = clock.movesToGo.getOrElse(math.max(MinMovesToGo, BaseMovesToGo - clock.moveNumber))
    math.max(1, estimate)

  /** Computes the ideal per-turn [[TimeBudget]] for the given clock. Pure; see the object docs for the formula. */
  def budget(clock: ClockState): TimeBudget =
    val reserve   = math.max(ReserveFloorMs, (ReserveFraction * clock.remainingMs).toLong)
    val spendable = math.max(0L, clock.remainingMs - reserve)
    val rawTarget = clock.incrementMs + spendable / movesToGo(clock)
    val hardCap   = math.max(MinThinkMs, (MaxFraction * clock.remainingMs).toLong)
    val capped    = math.min(math.max(rawTarget, MinThinkMs), hardCap)
    val target    =
      if spendable <= PanicThresholdMs then math.max(MinThinkMs, math.min(capped, PanicBudgetMs))
      else capped
    TimeBudget(target, hardCap)

  /** The conservative budget (in ms) the caller should turn into a search deadline.
    *
    * Subtracts the caller's transport/granularity `overheadBufferMs` from the target — the bot cannot interrupt an
    * in-flight rollout, and a worker round-trip adds latency, so the deadline is set short of the real allocation. The
    * buffer is caller knowledge (≈50 ms in-process, ≈150 ms across a Web Worker), hence a parameter rather than a
    * constant. Never returns less than [[MinThinkMs]].
    *
    * @param clock
    *   the moving side's clock snapshot
    * @param overheadBufferMs
    *   slack subtracted from the target to absorb rollout-overrun and transport latency
    */
  def budgetMs(clock: ClockState, overheadBufferMs: Long): Long =
    val b = budget(clock)
    math.max(MinThinkMs, math.min(b.targetMs, b.hardCapMs) - overheadBufferMs)
