package dicechess.engine.search

import dicechess.engine.domain.*
import scala.util.Random
import scala.util.boundary, boundary.break

/** Monte-Carlo Tree Search bot (difficulty 7).
  *
  * Builds a search tree using UCT to balance exploration and exploitation, performing Rao-Blackwellized rollouts at the
  * leaves.
  *
  * Nodes where `state.dicePool.isEmpty` are treated as Chance Nodes (representing the dice roll phase) and are sampled
  * randomly according to the uniform dice probability. Nodes where `state.dicePool.nonEmpty` are Decision Nodes and are
  * expanded and selected via UCT.
  */
object MctsSearch extends SearchAlgorithm with TimeBudgetedSearch:

  private val ProbScoreScale = 1_000_000

  // The UCT exploration constant. Sqrt(2) is standard.
  private val ExplorationConstant = math.sqrt(2)

  class MctsNode(val state: GameState, val parent: Option[MctsNode], val moveFromParent: Option[List[Move]]):
    var visits: Int              = 0
    var whiteWins: Double        = 0.0
    var blackWins: Double        = 0.0
    var children: List[MctsNode] = Nil

    var isExpanded: Boolean            = false
    var untriedPaths: List[List[Move]] = Nil

    def isChanceNode: Boolean = state.dicePool.isEmpty

    lazy val isTerminal: Boolean =
      !hasKing(state, Color.White) || !hasKing(state, Color.Black)

  private def hasKing(state: GameState, color: Color): Boolean =
    state.mailbox.toArray.exists(p => !p.isEmpty && p.pieceType == PieceType.King && p.color == color)

  override def findBestMove(state: GameState): Option[ScoredSequence] =
    // Default config when no deadline is given. Fixed iterations.
    findBestMoveWithIterations(state, 1000, new Random())

  def findBestMoveWithIterations(state: GameState, iterations: Int, random: Random): Option[ScoredSequence] = boundary:
    val paths = TurnGenerator.generateAllLegalTurnPaths(state)
    if paths.isEmpty then break(None)

    // Short-circuit immediate king captures
    val preScored = paths.map(p => SearchScoring.scorePath(state, p, Evaluator.evaluateMaterial))
    val captures  = preScored.filter(_.score == SearchScoring.TerminalWinScore)
    if captures.nonEmpty then break(Some(captures.minBy(_.moves.size)))

    val root = MctsNode(state, None, None)
    for _ <- 0 until iterations do
      val leaf   = select(root, random)
      val (w, b) = simulate(leaf, random)
      backpropagate(leaf, w, b)

    extractBestMove(root, paths)

  override def findBestMove(state: GameState, deadlineNanos: Long, random: Random): Option[ScoredSequence] = boundary:
    val paths = TurnGenerator.generateAllLegalTurnPaths(state)
    if paths.isEmpty then break(None)

    // Short-circuit immediate king captures
    val preScored = paths.map(p => SearchScoring.scorePath(state, p, Evaluator.evaluateMaterial))
    val captures  = preScored.filter(_.score == SearchScoring.TerminalWinScore)
    if captures.nonEmpty then break(Some(captures.minBy(_.moves.size)))

    val root = MctsNode(state, None, None)
    while System.nanoTime() < deadlineNanos do
      val leaf   = select(root, random)
      val (w, b) = simulate(leaf, random)
      backpropagate(leaf, w, b)

    extractBestMove(root, paths)

  private def extractBestMove(root: MctsNode, fallbackPaths: List[List[Move]]): Option[ScoredSequence] =
    if root.children.isEmpty then
      // Fallback if no iterations happened at all
      if fallbackPaths.isEmpty then None
      else Some(ScoredSequence(fallbackPaths.head, 0))
    else
      val bestChild = root.children.maxBy(_.visits)
      bestChild.moveFromParent match
        case None       => None // Should not happen for root children
        case Some(Nil)  => None // Pass
        case Some(path) =>
          val winRate = if root.state.activeColor.isWhite then bestChild.whiteWins / bestChild.visits
          else bestChild.blackWins / bestChild.visits

          val score = (winRate * ProbScoreScale).toInt
          Some(ScoredSequence(path, score))

  private def select(node: MctsNode, random: Random): MctsNode = boundary:
    var current = node
    while !current.isTerminal do
      if current.isChanceNode then
        val roll      = List(random.nextInt(6) + 1, random.nextInt(6) + 1, random.nextInt(6) + 1).sorted
        val nextState = current.state.withDicePool(roll)
        current.children.find(_.state.dicePool.sorted == roll) match
          case Some(child) => current = child
          case None        =>
            val newChild = MctsNode(nextState, Some(current), None)
            current.children = newChild :: current.children
            break(newChild)
      else
        if !current.isExpanded then
          val paths = TurnGenerator.generateAllLegalTurnPaths(current.state)
          current.untriedPaths = if paths.isEmpty then List(Nil) else random.shuffle(paths)
          current.isExpanded = true

        if current.untriedPaths.nonEmpty then
          val path = current.untriedPaths.head
          current.untriedPaths = current.untriedPaths.tail
          val nextState = if path.isEmpty then current.state.withDicePool(Nil).endTurn()
          else path.foldLeft(current.state)((s, m) => s.makeMove(m)).endTurn()
          val newChild = MctsNode(nextState, Some(current), Some(path))
          current.children = newChild :: current.children
          break(newChild)
        else current = bestUctChild(current)
    current

  private def bestUctChild(node: MctsNode): MctsNode =
    val activeColor = node.state.activeColor
    val C           = ExplorationConstant
    val lnN         = math.log(node.visits)
    node.children.maxBy { child =>
      if child.visits == 0 then Double.PositiveInfinity
      else
        val winRate = if activeColor.isWhite then child.whiteWins / child.visits
        else child.blackWins / child.visits
        winRate + C * math.sqrt(lnN / child.visits)
    }

  private def simulate(node: MctsNode, random: Random): (Double, Double) =
    if node.isTerminal then
      val whiteWon = if !hasKing(node.state, Color.Black) then 1.0 else 0.0
      val blackWon = if !hasKing(node.state, Color.White) then 1.0 else 0.0
      (whiteWon, blackWon)
    else
      val config = MonteCarloConfig(rollouts = 1, targetError = 0.0)
      val est    = MonteCarloEquity.estimate(node.state, config, random)
      (est.whiteWin, est.blackWin)

  private def backpropagate(node: MctsNode, whiteWin: Double, blackWin: Double): Unit =
    var current = Option(node)
    while current.isDefined do
      val n = current.get
      n.visits += 1
      n.whiteWins += whiteWin
      n.blackWins += blackWin
      current = n.parent
