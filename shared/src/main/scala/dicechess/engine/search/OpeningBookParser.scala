package dicechess.engine.search

/** Parses an opening book from its TSV form: mapping each canonical [[OpeningBook.key]] to the comma-separated
  * continuation.
  *
  * ```text
  * rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - BPR	e2e4,f1c4
  * ```
  */
object OpeningBookParser:

  /** Parses the opening-book TSV string, returning a decoding Exception on malformed input. */
  def parse(tsvStr: String): Either[Exception, Map[String, String]] =
    val parsed = tsvStr.linesIterator
      .filter(_.trim.nonEmpty)
      .map(_.split("\t", -1))
      .toList
      .partitionMap {
        case Array(k, v) => Right(k.trim -> v.trim)
        case other       => Left(s"Malformed line: ${other.mkString}")
      }

    parsed match
      case (Nil, valid) => Right(valid.toMap)
      case (errors, _)  => Left(new IllegalArgumentException(errors.mkString("; ")))
