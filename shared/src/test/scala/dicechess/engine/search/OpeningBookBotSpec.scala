package dicechess.engine.search

import dicechess.engine.domain.*
import munit.FunSuite

class OpeningBookBotSpec extends FunSuite:

  test("OpeningBookBot returns move from book if key matches") {
    // Basic starting position (just for test, not real initial FEN)
    val state = FenParser.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 BPR").toOption.get
    
    // The key is exactly what FenParser serializes when dice are present
    val key = FenParser.serialize(state)
    val targetPathStr = "e2e4,f1c4"
    
    val book = Map(key -> targetPathStr)
    
    val underlying = new SearchAlgorithm:
      def findBestMove(state: GameState): Option[ScoredSequence] = None
      override def shouldOfferDouble(state: GameState, currentStake: Int): Boolean = false
      override def shouldAcceptDouble(state: GameState, currentStake: Int): Boolean = false

    val bot = new OpeningBookBot(underlying, book)
    
    val result = bot.findBestMove(state)
    assert(result.isDefined)
    
    val moves = result.get.moves
    assertEquals(moves.size, 2)
    assertEquals(moves(0).fromSquare, Square('e', 2))
    assertEquals(moves(0).toSquare, Square('e', 4))
    assertEquals(moves(1).fromSquare, Square('f', 1))
    assertEquals(moves(1).toSquare, Square('c', 4))
  }

  test("OpeningBookBot delegates to underlying if key is missing") {
    val state = FenParser.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 BPR").toOption.get
    
    val book = Map.empty[String, String]
    
    // Underlying bot returns a dummy move
    val dummyMove = Move(Square('h', 2), Square('h', 3))
    val dummyResult = Some(ScoredSequence(List(dummyMove), 100))
    
    val underlying = new SearchAlgorithm:
      def findBestMove(state: GameState): Option[ScoredSequence] = dummyResult
      override def shouldOfferDouble(state: GameState, currentStake: Int): Boolean = false
      override def shouldAcceptDouble(state: GameState, currentStake: Int): Boolean = false

    val bot = new OpeningBookBot(underlying, book)
    
    val result = bot.findBestMove(state)
    assertEquals(result, dummyResult)
  }

  test("OpeningBookParser parses valid JSON map") {
    val json = """{"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 BPR": "e2e4,f1c4"}"""
    val parsed = OpeningBookParser.parse(json)
    
    assert(parsed.isRight)
    val map = parsed.toOption.get
    assertEquals(map.size, 1)
    assertEquals(map("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 BPR"), "e2e4,f1c4")
  }
