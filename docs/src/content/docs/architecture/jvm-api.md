---
title: JVM API (JvmApi)
description: Reference documentation for the Dice Chess Engine facade used by Java, Kotlin, and other non-Scala JVM callers.
---

The engine exposes `dicechess.engine.jvmapi.JvmApi` to JVM consumers that are not written in
Scala — [dicechess-bot-java](https://github.com/rabestro/dicechess-bot-java) is the reference
consumer. It is the JVM row's counterpart to the JS row's
[`EngineFacade`](/dicechess-engine-scala/architecture/javascript-api/): a deliberately narrow
surface that hides the Scala-shaped parts of the API.

For dependency coordinates and authentication, see
[Maven Artifact & JVM Integration](/dicechess-engine-scala/guidelines/maven-artifact/).

## Why a facade exists

Most of the engine's Scala API is reachable from Java only in theory. Three constructs break at
the language boundary:

- **`Either` returns.** `FenParser.parse` gives back `Either[String, GameState]`, which from Java
  means chains like `parseResult.left().toOption().isDefined()` before any value can be read.
- **Extension methods.** `GameState.activeColor` and `GameState.makeMove` compile onto a synthetic
  `$package` class with no ordinary Java entry point. Worse, `makeMove` is overloaded, so Scala
  disambiguates it via `@targetName` — the JVM name a Java caller would need is
  `makeMove_Move`, an implementation detail no contract promises to keep.
- **Opaque types.** `Move` is an `opaque type Move = Int`, so it erases on the JVM. From Java,
  `TurnGenerator.generateAllLegalTurnPaths`'s `List[List[Move]]` arrives as an unchecked
  `List[List[Object]]` of boxed integers with no type-safe way to read it back.

Binding to the facade instead of working around these is what keeps a consumer independent of
engine internals. Treat everything outside `JvmApi` as internal, whatever its visibility.

## `JvmApi`

### `parseDfen`

Parses a DFEN string (FEN extended with a 7th field for the pending dice pool) into a `GameState`.

```java
static GameState parseDfen(String dfen)
```

**Returns:** the parsed `GameState`. **Throws** `IllegalArgumentException` if the DFEN is invalid;
the message is the parser's own error text. This is the one place the engine deliberately departs
from its `Either`-only error convention — translating into an exception a Java caller can `catch`
is the method's entire purpose.

---

### `activeColor`

The color to move.

```java
static int activeColor(GameState state)
```

**Returns:** `0` for White, `1` for Black. (`Color` is an opaque type over `Int`, so it erases to a
plain `int` — the value is directly usable as the `color` argument of the engine's evaluators.)

---

### `legalTurns`

Every legal **full turn** playable from the position — not individual micro-moves.

```java
static java.util.List<JvmApi.Turn> legalTurns(GameState state)
```

Each `Turn` carries both the turn's UCI micro-moves and the position they lead to, read through
accessors named after the fields:

```java
JvmApi.Turn turn = ...;
java.util.List<String> uci = turn.uci();
GameState finalState = turn.finalState();
```

`Turn` is a Scala case class, so it reads like a Java record — accessor per field, plus `equals`,
`hashCode`, and `toString` — but it is **not** a `java.lang.Record` and cannot be used in record
deconstruction patterns. Its compiled form also carries `scala.Product` and Scala's `copy`
machinery; those members are visible to a Java caller but are not part of this facade's contract.

**Returns:** a list of legal turns, empty when the roll has no legal turn (a forced pass).

`finalState` is what makes this a single call rather than an enumerate-then-apply loop: a bot
scoring candidate turns feeds it straight to an evaluator. Note it has **not** been passed through
`endTurn()` — the active color has not flipped and the dice pool has not been cleared.

## Working with the platform's `legalMoves`

The Dice Chess platform delivers its own legal-turn tree in the webhook envelope
(`TurnContext.legalMoves`), so a bot may already hold the UCI sequences before calling the engine.

There is intentionally **no** `applyTurn(state, List<String>)` on this facade. The platform treats
UCI strings as opaque tokens matched by exact string equality against engine-generated paths and
never decodes them; a decoder here would be a second implementation that can drift from the
encoder. Match the externally-sourced sequence against `legalTurns`' own `uci` field to obtain the
corresponding `finalState`.

That also covers the case where the tree is absent: `legalMoves` is `null` whenever the enumeration
exceeded the platform's inline size cap, and `legalTurns` is the fallback that keeps a bot playing
rather than forfeiting.

## API documentation in the IDE

There is no separate JavaDoc build — the engine has no Java main sources to generate one from.
The published `-javadoc.jar` contains rendered **Scaladoc** instead, which is the standard Maven
convention for Scala artifacts. IDEs attach it exactly as they would real JavaDoc, so Java and
Kotlin callers get `JvmApi`'s documentation on hover with no extra setup.
