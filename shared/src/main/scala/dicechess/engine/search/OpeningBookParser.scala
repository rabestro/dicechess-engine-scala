package dicechess.engine.search

import io.circe.*
import io.circe.parser.*

/** Deserialises an opening book from its on-the-wire JSON form: a flat object mapping each canonical
  * [[OpeningBook.key]] to the comma-separated continuation.
  *
  * ```scala
  * {
  *   "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - BPR": "e2e4,f1c4"
  * }
  * ```
  */
object OpeningBookParser:

  /** Parses the opening-book JSON map, returning a decoding [[io.circe.Error]] on malformed input. */
  def parse(jsonStr: String): Either[Error, Map[String, String]] =
    decode[Map[String, String]](jsonStr)
