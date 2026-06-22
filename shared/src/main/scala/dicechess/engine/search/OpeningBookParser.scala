package dicechess.engine.search

import io.circe.*
import io.circe.parser.*

object OpeningBookParser:

  /** Parses a JSON string containing the opening book map.
    *
    * The expected format is:
    * ```scala
    * {
    *   "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - BPR": "e2e4,f1c4"
    * }
    * ```
    */
  def parse(jsonStr: String): Either[Error, Map[String, String]] =
    decode[Map[String, String]](jsonStr)
