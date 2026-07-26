---
title: Time Management
description: How the engine turns a game clock into a per-turn thinking budget — the policy/honouring split, the budget formula and its constants, which algorithms are budgeted, and why timed measurements are machine-dependent.
sidebar:
  order: 5
---

A bot that plays perfectly but loses on time scores zero. Time management is what stands between
"searches well" and "wins games", and in this engine it is deliberately split into **two independent
layers** that know nothing about each other:

| Layer | What it decides | Where it lives | Determinism |
| --- | --- | --- | --- |
| **(a) Policy** | *How much* time this turn deserves | [`TimeManager`](https://github.com/rabestro/dicechess-engine-scala/blob/main/shared/src/main/scala/dicechess/engine/search/TimeManager.scala) | Pure — fully unit-testable |
| **(b) Honouring** | *Stopping* when that time is spent | [`TimeBudgetedSearch`](https://github.com/rabestro/dicechess-engine-scala/blob/main/shared/src/main/scala/dicechess/engine/search/TimeBudgetedSearch.scala) implementors | Wall-clock — machine-dependent |

The caller joins them: it asks `TimeManager` for a budget in milliseconds, converts that into a
`System.nanoTime` deadline, and hands the deadline to the search. Nothing else crosses the boundary.

Keeping the policy in one shared object — rather than inside whichever algorithm happens to need it —
means the JS API, the offline arena, and any future server all inherit the same tested behaviour
instead of each re-deriving it. It also means the policy is pure `Long`/`Double` arithmetic with no
clock reads, so it can be tested exhaustively without wall-clock flakiness. The *search* side cannot
be tested that way, which is precisely why the split exists.

## The budget formula

`TimeManager.budget` maps a `ClockState` (remaining time, increment, move number, optional
moves-to-go) to a `TimeBudget` of `targetMs` and `hardCapMs`. In order:

$$
\text{reserve} = \max(\text{ReserveFloor},\ \text{ReserveFraction} \times \text{remaining})
$$

$$
\text{spendable} = \max(0,\ \text{remaining} - \text{reserve})
$$

$$
\text{target}_{\text{raw}} = \text{increment} + \left\lfloor \frac{\text{spendable}}{\text{movesToGo}} \right\rfloor
$$

$$
\text{hardCap} = \max(\text{MinThink},\ \text{MaxFraction} \times \text{remaining})
$$

$$
\text{target} = \operatorname{clamp}\bigl(\text{target}_{\text{raw}},\ \text{MinThink},\ \text{hardCap}\bigr)
$$

with a final override: while $\text{spendable} \le \text{PanicThreshold}$, the target is additionally
capped at $\text{PanicBudget}$.

The moves-to-go estimate tapers with the move number unless the caller supplies one explicitly:

$$
\text{movesToGo} = \max\bigl(1,\ \max(\text{MinMovesToGo},\ \text{BaseMovesToGo} - \text{moveNumber})\bigr)
$$

### The constants, and why each exists

| Constant | Value | Why it is there |
| --- | --- | --- |
| `ReserveFloorMs` | 300 ms | An absolute sliver never spent, so transport latency alone can never flag the clock. |
| `ReserveFraction` | 0.05 | On a healthy clock the reserve should scale with it, not stay a fixed crumb — 5 % of a 10-minute clock is 30 s of genuine safety margin. |
| `BaseMovesToGo` | 30 | Spreading the clock requires a guess at how many turns remain; 30 is the opening estimate. |
| `MinMovesToGo` | 12 | Without a floor, a long game would drive the estimate toward zero and hand a single turn the whole clock. |
| `MaxFraction` | 0.20 | The hard ceiling: no single turn may bet more than a fifth of what is left, however attractive the position looks. |
| `MinThinkMs` | 20 ms | Even in the worst case the bot gets *some* thinking time rather than a zero-length budget. |
| `PanicThresholdMs` | 2000 ms | Below two spendable seconds the game is about survival, not quality. |
| `PanicBudgetMs` | 200 ms | In panic, stretch the remainder across several turns instead of spending it on one. |

The two clamps do different jobs and both are needed. The **hard cap** is proportional, so it scales
with the clock and binds on every turn. The **panic clamp** is absolute, and takes over exactly when
a proportional cap stops being protective — 20 % of 1.5 s is 300 ms, which is still enough to flag if
spent three turns in a row.

`TimeManager.budgetMs(clock, overheadBufferMs)` is the convenience the callers actually use: it
returns `min(target, hardCap)` minus an overhead buffer. The buffer is *caller* knowledge (≈50 ms
in-process, ≈150 ms across a Web Worker), so it is a parameter rather than a constant.

## Behaviour across controls

The same formula produces qualitatively different behaviour depending on whether there is an
increment. Both tables below are computed directly from the shipped constants.

### Sudden death (1+0)

| Remaining | Move | movesToGo | Spendable | **Target** | Hard cap |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 60 000 ms | 1 | 29 | 57 000 ms | **1 965 ms** | 12 000 ms |
| 30 000 ms | 10 | 20 | 28 500 ms | **1 425 ms** | 6 000 ms |
| 10 000 ms | 20 | 12 | 9 500 ms | **791 ms** | 2 000 ms |
| 4 000 ms | 30 | 12 | 3 700 ms | **308 ms** | 800 ms |
| 2 000 ms | 40 | 12 | 1 700 ms | **141 ms** | 400 ms |
| 500 ms | 50 | 12 | 200 ms | **20 ms** | 100 ms |

With no increment the clock only ever drains, so the target decays monotonically. The last row is the
panic floor doing its job: at half a second left the bot blitzes legal moves rather than flagging.

### Fischer (10+10)

| Remaining | Move | movesToGo | Spendable | **Target** | Hard cap |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 600 000 ms | 1 | 29 | 570 000 ms | **29 655 ms** | 120 000 ms |
| 400 000 ms | 10 | 20 | 380 000 ms | **29 000 ms** | 80 000 ms |
| 200 000 ms | 20 | 12 | 190 000 ms | **25 833 ms** | 40 000 ms |
| 60 000 ms | 30 | 12 | 57 000 ms | **12 000 ms** | 12 000 ms |
| 20 000 ms | 40 | 12 | 19 000 ms | **4 000 ms** | 4 000 ms |
| 5 000 ms | 50 | 12 | 4 700 ms | **1 000 ms** | 1 000 ms |
| 1 500 ms | 60 | 12 | 1 200 ms | **200 ms** | 300 ms |

Two regimes are visible. While the clock is healthy the `increment` term dominates and the bot
happily spends ~25–30 s per turn. From the fourth row on, the **hard cap binds** (target equals hard
cap exactly) and the increment term stops mattering.

Note what happens at 20 000 ms remaining: the target is **4 000 ms, well below the 10 000 ms
increment**. That looks wasteful — the increment would refund more than is being spent — but it is
deliberate. The increment is credited *after* the turn completes, so a bot that spends its full
increment while the clock is low can still flag before the refund arrives. The policy trades some
time-utilisation for a guarantee it never loses on time. The constants are tunable if that trade ever
looks wrong.

## Which algorithms honour a deadline

`TimeBudgetedSearch` is a **capability mix-in**, not a method on the base `SearchAlgorithm` trait.
That is a deliberate design choice: a one-ply heuristic bot returns in microseconds, and forcing it to
accept a deadline it can never miss would be ceremony that teaches the reader nothing. A caller that
needs clock handling checks for the capability; instant bots simply do not have it.

| Algorithm | Budgeted? | Yield granularity — the unit of uninterruptible work |
| --- | --- | --- |
| `MonteCarloSearch` | Yes | One rollout (candidates additionally get equal time slices) |
| `ExpectimaxSearch` | Yes | One dice roll inside a chance node — $1/56$ of a root candidate |
| `OnnxExpectimaxSearch` | Yes | Delegates to `ExpectimaxSearch` |
| `OnnxEvalSearch` | Yes | One batch of 32 candidates |
| `OpeningBookBot` | Inherited | `decorate` preserves the wrapped bot's capability and forwards the deadline on a book miss |
| `RandomSearch`, `GreedySearch`, `GreedySearchV2`, `AggressiveSearch`, `CheckmateAwareSearch` | No, by design | Instant one-ply bots — nothing to interrupt |

Every budgeted algorithm also honours the **anytime contract**: when the deadline elapses it returns
the best turn found so far, and a legal turn always comes back if one exists — including when the
deadline is already past on entry.

## Granularity is part of the contract

"The search stops at the deadline" is a convenient fiction. A search can only stop where it *yields*,
so what the contract really promises is "stops at the first yield point after the deadline". The
overshoot is therefore bounded by the coarsest unit of uninterruptible work — which is why the table
above lists that unit for every budgeted algorithm.

This is not a theoretical concern. `ExpectimaxSearch` originally checked the clock **between root
candidates only**. At `candidateLimit = 24` on a single core, one candidate measured a **6.5 s median
against a 1.9 s allocation — a 3.4× overshoot** the caller had no way to prevent
([#496](https://github.com/rabestro/dicechess-engine-scala/issues/496)). The deadline was, in
practice, advisory. The fix was to move the check inside the chance node, one dice roll at a time,
making the interruption unit roughly $1/56$ of what it had been.

The lesson generalises to any new implementor: **keep the unit of uninterruptible work well below a
realistic per-move budget**, and guard the clock read so the untimed path pays nothing for it.

## Timed measurements are not reproducible

The engine has two arenas, and they make opposite guarantees:

- The **untimed** arena ([`BotMatchRunner`](https://github.com/rabestro/dicechess-engine-scala/blob/main/jvm/src/main/scala/dicechess/engine/bench/BotMatchRunner.scala),
  `OnnxArenaRunner`, `OnnxExpectimaxArenaRunner`) is fully **seeded and reproducible**. The same
  binary and the same seed produce the same games, bit for bit, on any machine. A win rate from it is
  a property of the algorithms.
- The **timed** arena (`TimedArenaRunner`, `OnnxTimedArenaRunner`) is **machine-dependent by
  construction**. A slower box searches fewer candidates inside the same wall-clock budget, so the
  same code produces different play — and a different win rate — on different hardware. A number from
  it is a property of the algorithms *and the box that ran them*.

Only ever compare timed results **within one box and one session**. Carrying a timed win rate across
machines is the single easiest way to misread this harness, and the comparison it invites is not
merely noisy — it is measuring a different thing.

The flip side is what the timed arena uniquely sees: under a clock, the *cost* of a position becomes a
strength term. A model twice as expensive searches half as wide inside the same budget, and the
untimed arena is blind to that entirely. Whenever a seeded arena and a live ladder disagree about
which of two bots is stronger, this is the first suspect worth ruling out.
