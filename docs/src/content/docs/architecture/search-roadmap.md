---
title: Search Roadmap & Evaluation
description: The staged plan for improving Dice Chess search and the benchmark protocol used to validate each upgrade.
sidebar:
  order: 7
---

The current engine already has two useful search baselines:

* `RandomSearch` proves that full-turn Dice Chess path generation works end to end.
* `GreedySearch` proves that the engine can score and rank complete legal turn paths.

That is enough to start a disciplined search-improvement program, but not enough to justify future algorithm changes without measurement. This document defines both:

1. the implementation roadmap for stronger search algorithms
2. the evaluation protocol that every new algorithm must pass before it replaces the current baseline

## Goals

The search roadmap should improve move quality without losing the properties that already matter:

* legal move selection under the maximum micro-moves rule
* deterministic and reproducible testing
* predictable runtime for browser and JVM usage
* a clear migration path from simple heuristics to probabilistic tree search

Future changes should therefore be judged on two axes:

* **playing strength** against the current baseline
* **cost** in runtime, implementation complexity, and test burden

## Baseline

The current baseline is `GreedySearch`.

It has several advantages:

* it already reasons over the full Dice Chess turn, not a single micro-move
* it is deterministic
* it is easy to explain and debug
* it creates a stable reference point for head-to-head testing

The baseline must remain available even after stronger algorithms are added. It is the control group for all future experiments.

## Roadmap

The search roadmap is intentionally incremental. Each stage should land only after it is measurable against the baseline.

### Stage 1: GreedySearch V2

This stage keeps the existing full-turn enumeration model and upgrades the scoring logic.

Planned improvements:

* explicit terminal scoring for king capture
* strong penalties for ending a turn in a position where the opponent can easily capture our king
* mobility-aware evaluation
* promotion-aware evaluation
* simple tactical bonuses for unblock-and-capture sequences inside the same turn

The goal is to keep the algorithm cheap while removing obvious distortions caused by material-only evaluation.

**Milestone fit**:

* initial terminal-scoring and king-safety work belongs to **v0.4 - Basic Bot & Gameplay**
* richer static heuristics naturally extend into **v0.5 - Evaluation & Heuristics**

### Stage 2: Reply-Aware Greedy Search

This stage adds a single opponent reply layer after our candidate turn path.

Expected behavior:

1. enumerate our legal turn paths
2. apply each path
3. evaluate the opponent's best answer under sampled or complete dice outcomes
4. rank our move by either expected value or a more conservative risk-aware policy

This is the cheapest meaningful step beyond pure greedy play. It starts to model tactical punishment on the opponent turn without committing to a full probabilistic search tree.

**Milestone fit**:

* mostly **v0.5 - Evaluation & Heuristics**
* can begin to overlap with **v0.6 - Expectimax Search Engine**

### Stage 3: Expectimax Search

This is the long-term target for the engine.

Expected characteristics:

* chance nodes for dice outcomes
* decision nodes for selecting the best legal full-turn path
* bounded depth and/or time controls
* support for cached evaluations and transpositions
* eventual parallel expansion of probability branches

Expectimax should not be treated as a first implementation step. It depends on the earlier stages to provide:

* stable evaluation semantics
* reliable benchmark methodology
* representative scenario suites
* clear performance budgets

**Milestone fit**:

* primarily **v0.6 - Expectimax Search Engine**

## Evaluation Pyramid

Every new algorithm must be validated at three levels.

### Level 1: Unit Correctness

These tests prove that the algorithm respects core game semantics and obvious tactical priorities.

Required examples:

* immediate king capture is always preferred over any non-terminal material sequence
* a preparatory micro-move may be chosen when it enables a stronger same-turn capture
* the maximum micro-moves rule is never violated
* castling still consumes the correct dice and is scored correctly
* promotion branches are ranked consistently

This level protects correctness and prevents regressions that would otherwise be hidden inside large simulations.

### Level 2: Scenario Suite

This suite is a curated set of fixed positions designed to stress the evaluator and shallow search logic.

Recommended scenario groups:

* immediate king capture
* unblock-and-capture
* promotion race
* king exposure after material gain
* sacrificial trap
* high-mobility versus low-mobility positions
* symmetric positions used to verify deterministic tie-breaking

Each scenario should define:

* FEN
* dice multiset
* expected preferred line or set of acceptable lines
* reason the case exists

This suite should remain small enough to run in every local `mise run check`.

### Level 3: Mass Simulation

This is the acceptance layer for comparing playing strength.

Each challenger algorithm should play a large head-to-head match against the baseline:

* baseline as White, challenger as Black
* challenger as White, baseline as Black
* fixed seed lists for reproducibility
* repeated across multiple starting positions or position classes

The simulation harness should support at least these position sources:

* standard starting position
* curated tactical middlegames
* simplified endgames
* regression positions extracted from failing tests or surprising previous games

## Match Protocol

The benchmark harness should follow a stable protocol so that results remain comparable over time.

### Required controls

* deterministic PRNG seeding
* explicit algorithm identity in output reports
* symmetric color assignment
* turn/time budget settings recorded in the report
* fixed resignation, repetition, and move-limit rules

### Required metrics

At minimum, collect:

* win rate
* loss rate
* draw rate, if draws exist in the implemented rules
* average game length
* average decision time per turn
* average number of candidate paths or nodes evaluated
* king-capture rate

Useful secondary metrics:

* average final material balance
* score volatility between turns
* per-position or per-seed breakdown
* runtime distribution, not just mean runtime

### Acceptance criteria

A challenger should replace the baseline only when all of the following are true:

* it passes unit correctness and scenario-suite checks
* it shows a statistically meaningful improvement in head-to-head results
* it does not introduce an unacceptable runtime regression for the intended environment
* its behavior is explainable enough to debug and maintain

If a challenger improves win rate but makes runtime or determinism substantially worse, it should remain experimental until those costs are understood.

## Reporting Format

Each experiment should produce a concise machine-readable and human-readable report.

Recommended report fields:

* algorithm name and version
* compared baseline
* git commit hash
* seed list identifier
* starting position set identifier
* total games
* wins, losses, draws
* average turns per game
* average time per turn
* notes on known limitations

This makes search changes reviewable inside pull requests instead of relying on anecdotal observations.

## Suggested Issue Decomposition

The roadmap is easiest to implement as a sequence of narrow issues:

1. `GreedySearch V2: terminal king-capture scoring`
2. `GreedySearch V2: king safety heuristic`
3. `GreedySearch V2: mobility and promotion heuristics`
4. `ReplyAwareGreedySearch: one-ply opponent response`
5. `Benchmark harness: bot-vs-bot simulation`
6. `Search evaluation reports and fixtures`
7. `Expectimax search skeleton`

The benchmark harness should land early enough that algorithm changes are measured before the search tree becomes more complex.

## Milestone Mapping

The recommended milestone mapping is:

* **v0.4 - Basic Bot & Gameplay**
  * terminal king-capture scoring
  * baseline preservation
  * first benchmark harness skeleton
* **v0.5 - Evaluation & Heuristics**
  * king safety, mobility, promotion heuristics
  * reply-aware greedy search
  * curated scenario suite expansion
* **v0.6 - Expectimax Search Engine**
  * expectimax implementation
  * chance-node evaluation
  * transposition and parallel search improvements

This keeps the project aligned with the approved milestones already published in the repository.

## Practical Rule

Until expectimax is production-ready, every search change should answer one concrete question:

> Does this algorithm beat the current `GreedySearch` baseline often enough to justify its added complexity?

If that answer is not backed by repeatable data, the change is not ready to replace the baseline.
