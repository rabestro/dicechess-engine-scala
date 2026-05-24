package dicechess.engine.domain

import munit.ScalaCheckSuite
import org.scalacheck.Prop.*
import org.scalacheck.Gen
import org.scalacheck.Arbitrary

class PropertySpec extends ScalaCheckSuite:

  // --- Generators ---

  val colorGen: Gen[Color] = Gen.oneOf(Color.White, Color.Black)

  val pieceTypeGen: Gen[PieceType] = Gen.oneOf(PieceType.all)

  val pieceGen: Gen[Piece] = for
    color <- colorGen
    pt    <- pieceTypeGen
  yield Piece(color, pt)

  val squareGen: Gen[Square] = Gen.choose(0, 63).map(Square.fromIndex)

  val bitboardGen: Gen[Bitboard] = Gen.long.map(Bitboard.apply)

  // A generator for internally consistent GameState instances
  val gameStateGen: Gen[GameState] = for
    activeColor <- colorGen

    // Choose how many pieces on the board (1 to 32)
    numPieces <- Gen.choose(1, 32)

    // Choose distinct squares
    squaresIndices <- Gen.pick(numPieces, 0 to 63).map(_.toList)

    // Generate pieces for these squares
    pieces <- Gen.listOfN(numPieces, pieceGen)

    mailbox = squaresIndices
      .zip(pieces)
      .map { case (idx, piece) =>
        Square.fromIndex(idx) -> piece
      }
      .toMap

    // Build Bitboards from Mailbox
    whitePieces = mailbox
      .collect { case (sq, p) if p.color.isWhite => sq }
      .foldLeft(Bitboard.empty)((bb, sq) => bb | Bitboard.fromSquare(sq))
    blackPieces = mailbox
      .collect { case (sq, p) if p.color.isBlack => sq }
      .foldLeft(Bitboard.empty)((bb, sq) => bb | Bitboard.fromSquare(sq))
    pawns = mailbox
      .collect { case (sq, p) if p.pieceType == PieceType.Pawn => sq }
      .foldLeft(Bitboard.empty)((bb, sq) => bb | Bitboard.fromSquare(sq))
    knights = mailbox
      .collect { case (sq, p) if p.pieceType == PieceType.Knight => sq }
      .foldLeft(Bitboard.empty)((bb, sq) => bb | Bitboard.fromSquare(sq))
    bishops = mailbox
      .collect { case (sq, p) if p.pieceType == PieceType.Bishop => sq }
      .foldLeft(Bitboard.empty)((bb, sq) => bb | Bitboard.fromSquare(sq))
    rooks = mailbox
      .collect { case (sq, p) if p.pieceType == PieceType.Rook => sq }
      .foldLeft(Bitboard.empty)((bb, sq) => bb | Bitboard.fromSquare(sq))
    queens = mailbox
      .collect { case (sq, p) if p.pieceType == PieceType.Queen => sq }
      .foldLeft(Bitboard.empty)((bb, sq) => bb | Bitboard.fromSquare(sq))
    kings = mailbox
      .collect { case (sq, p) if p.pieceType == PieceType.King => sq }
      .foldLeft(Bitboard.empty)((bb, sq) => bb | Bitboard.fromSquare(sq))

    // Generate castling string from a subset of K, Q, k, q
    castlingChars <- Gen.someOf(List('K', 'Q', 'k', 'q'))
    castlingRights = if castlingChars.isEmpty then "-" else castlingChars.mkString("")

    // Generate enPassant square on rank 3 or 6 or None
    enPassantOpt <- Gen.option(for
      file <- Gen.choose('a', 'h')
      rank <- Gen.oneOf(3, 6)
    yield Square(file, rank))

    halfMoveClock  <- Gen.choose(0, 100)
    fullMoveNumber <- Gen.choose(1, 500)
  yield GameState(
    whitePieces = whitePieces,
    blackPieces = blackPieces,
    pawns = pawns,
    knights = knights,
    bishops = bishops,
    rooks = rooks,
    queens = queens,
    kings = kings,
    mailbox = mailbox,
    activeColor = activeColor,
    castlingRights = castlingRights,
    enPassant = enPassantOpt,
    halfMoveClock = halfMoveClock,
    fullMoveNumber = fullMoveNumber
  )

  // Custom implicits for ScalaCheck
  given Arbitrary[Color]     = Arbitrary(colorGen)
  given Arbitrary[PieceType] = Arbitrary(pieceTypeGen)
  given Arbitrary[Piece]     = Arbitrary(pieceGen)
  given Arbitrary[Square]    = Arbitrary(squareGen)
  given Arbitrary[Bitboard]  = Arbitrary(bitboardGen)
  given Arbitrary[GameState] = Arbitrary(gameStateGen)

  // --- Properties ---

  property("Square index mapping is perfectly bidirectional") {
    forAll(Gen.choose(0, 63)) { (idx: Int) =>
      val sq = Square.fromIndex(idx)
      sq.index == idx
    }
  }

  property("Square notation mapping is perfectly bidirectional") {
    forAll(squareGen) { (sq: Square) =>
      Square.fromNotation(sq.toNotation) == Some(sq)
    }
  }

  property("Piece packing roundtrips color and pieceType") {
    forAll(colorGen, pieceTypeGen) { (c: Color, pt: PieceType) =>
      val p = Piece(c, pt)
      p.color == c && p.pieceType == pt
    }
  }

  property("MicroMove packing roundtrips origin, destination, and promotion") {
    forAll(squareGen, squareGen, Gen.option(pieceTypeGen)) { (from: Square, to: Square, promo: Option[PieceType]) =>
      val mm = MicroMove(from, to, promo)
      mm.from == from && mm.to == to && mm.promotion == promo
    }
  }

  property("Bitboard addition and removal contains target square") {
    forAll(bitboardGen, squareGen) { (bb: Bitboard, sq: Square) =>
      val added = bb.add(sq)
      assert(added.contains(sq))

      val removed = added.remove(sq)
      assert(!removed.contains(sq))
    }
  }

  property("Bitboard popcount count equals elements in contains") {
    forAll(bitboardGen) { (bb: Bitboard) =>
      val squaresCount = (0 until 64).count(idx => bb.contains(Square.fromIndex(idx)))
      bb.count == squaresCount
    }
  }

  property("Bitboard bitwise operations correspond to boolean algebra") {
    forAll(bitboardGen, bitboardGen) { (bb1: Bitboard, bb2: Bitboard) =>
      val and = bb1 & bb2
      val or  = bb1 | bb2
      val xor = bb1 ^ bb2

      (0 until 64).forall { idx =>
        val sq = Square.fromIndex(idx)
        (and.contains(sq) == (bb1.contains(sq) && bb2.contains(sq))) &&
        (or.contains(sq) == (bb1.contains(sq) || bb2.contains(sq))) &&
        (xor.contains(sq) == (bb1.contains(sq) ^ bb2.contains(sq)))
      }
    }
  }

  property("FEN round-trip serialization and parsing yields identical GameState") {
    forAll(gameStateGen) { (state: GameState) =>
      val serialized = FenParser.serialize(state)
      val parsed     = FenParser.parse(serialized)
      parsed == Right(state)
    }
  }
