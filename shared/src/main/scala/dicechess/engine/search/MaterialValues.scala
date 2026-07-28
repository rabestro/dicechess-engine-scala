package dicechess.engine.search

/** The centipawn scale every engine-side evaluation and safety metric is priced in (#510).
  *
  * The values are the classical relative piece values scaled by 100, so a pawn is the unit and integer arithmetic
  * carries fractional-pawn precision without floating point on a hot path.
  *
  * '''Why this is a shared object and not a constant on [[Evaluator]].''' Two consumers must agree on it by
  * construction, not by convention: [[Evaluator]] prices positions, and [[PieceSafety]] prices the material a side has
  * en prise — the arena's hang telemetry. Those two numbers are compared against each other in practice ("did the
  * safety signal reduce the blunders the telemetry counts"), which only means something while both use one scale.
  * [[PieceSafety]] is the lower-level primitive of the pair, so having it reach into the evaluator for a rook's price
  * would invert the layering; both depend on this instead.
  *
  * '''The king is priced, unlike in ordinary chess evaluators.''' Dice Chess is won by actually capturing the king
  * rather than by mate, so a king is a capturable piece whose loss ends the game — [[Evaluator.evaluateMaterial]] gives
  * it a value far above the rest of the army so no material gain can outweigh losing it. [[PieceSafety]] never needs
  * it: an attacked king is check, a separate concept with its own machinery, and `hangingSquares` excludes kings by
  * construction.
  *
  * @note
  *   Deliberately NOT the scale used by the ONNX feature extractors. [[OnnxFeatures]] prices pieces at 1/3/3/5/9,
  *   because that vector is the frozen input contract of a trained model; changing its unit would invalidate the
  *   deployed weights. The two scales are proportional for the non-king pieces and are meant to stay independent.
  */
object MaterialValues:

  val Pawn: Int   = 100
  val Knight: Int = 300
  val Bishop: Int = 300
  val Rook: Int   = 500
  val Queen: Int  = 900

  /** Above the summed value of an entire starting army, so no capture sequence can price a king loss as acceptable. */
  val King: Int = 10000
