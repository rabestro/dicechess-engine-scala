package dicechess.engine.jvmapi

import dicechess.engine.domain.*
import dicechess.engine.search.TurnGenerator

import java.util as ju
import scala.jdk.CollectionConverters.*

/** A JVM-language-agnostic facade over the engine's Scala API — the JVM-row counterpart to the JS row's `EngineFacade`
  * (`js/.../EngineFacade.scala`). For a Java, Kotlin, or other JVM-language caller, the plain Scala API has three sharp
  * edges this facade exists to remove:
  *
  *   - `Either[String, GameState]` (`FenParser.parse`) has no idiomatic Java consumption; [[parseDfen]] converts the
  *     `Left` case to an exception.
  *   - Extension methods (`GameState.activeColor`, `GameState.makeMove`) compile onto a synthetic `$package` object,
  *     not a real class — reachable from Java only via reflection or a decompiler. [[activeColor]] and [[legalTurns]]
  *     wrap the ones a caller actually needs behind ordinary static methods.
  *   - `Move` is an opaque `Int`: erasure makes a bare `List[List[Move]]` look, from Java, like an unchecked
  *     `List[List[Object]]` of boxed integers with no safe way to read it back. [[legalTurns]] decodes each path to its
  *     `java.util.List[String]` of UCI tokens (and the position after playing it) so the opaque type never crosses the
  *     boundary.
  *
  * '''What this deliberately does not do''': there is no `applyTurn(state, List[String])` that re-parses UCI tokens
  * back into moves. play-api's protocol treats UCI strings as opaque tokens matched by exact string equality against
  * engine-generated legal paths, never independently decoded (see `WebhookBot`'s Scaladoc) — a second decoder here
  * would be a fifth place that can drift from the encoder in `Move.toUci`. A caller that already holds a UCI move list
  * (e.g. from the platform's inline `legalMoves`) matches it against [[legalTurns]]'s own `uci` field to get the
  * corresponding [[Turn.finalState]], instead of decoding the strings itself.
  */
object JvmApi:

  /** One complete legal turn: its micro-moves as UCI tokens, in order (e.g. `["e2e4"]` or `["d2d4", "d4d5"]`), and the
    * position reached by playing all of them. `finalState` has '''not''' been passed through `GameState.endTurn()` —
    * the active color has not flipped and the dice pool has not been cleared — because nothing in [[legalTurns]]'s only
    * current use (scoring candidate turns) needs a turn-ended state, and guessing at that boundary on a caller's behalf
    * would be speculative.
    */
  final case class Turn(uci: ju.List[String], finalState: GameState)

  /** Parses a DFEN string (FEN extended with a 7th field for the pending dice pool) into a [[GameState]].
    *
    * @throws IllegalArgumentException
    *   if `dfen` is not a valid DFEN — the message is `FenParser`'s own parse error
    */
  def parseDfen(dfen: String): GameState =
    // The one deliberate exception to this repo's Either-only error convention: this method's entire purpose is
    // translating FenParser's Either into something a Java caller can catch, so IllegalArgumentException (not
    // sys.error's RuntimeException) is the point, not a shortcut.
    FenParser.parse(dfen).fold(msg => throw IllegalArgumentException(msg), identity) // scalafix:ok(DisableSyntax.throw)

  /** The color to move in `state` — `0` (White) or `1` (Black); see [[Color]]. Exposed here only because
    * `GameState.activeColor` is an extension method a Java caller cannot otherwise reach without reflection.
    */
  def activeColor(state: GameState): Color = state.activeColor

  /** Every legal turn playable from `state`, each as its UCI micro-move sequence plus the resulting position — the
    * fallback for when the platform's inline `legalMoves` tree was elided by its size cap (`TurnContext.legalMoves` is
    * `null` in that case; see dicechess-bot-runtime). Empty when the roll has no legal turn (a forced pass).
    */
  def legalTurns(state: GameState): ju.List[Turn] =
    TurnGenerator
      .generateAllLegalTurnPaths(state)
      .map { path =>
        val finalState = path.foldLeft(state)(_.makeMove(_))
        Turn(path.map(_.toUci).asJava, finalState)
      }
      .asJava
