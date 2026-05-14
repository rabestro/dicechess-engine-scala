package dicechess.engine.domain

import scala.annotation.targetName

extension (state: GameState)
  /** Applies a micro-move (Turn-based) to the current game state.
    *
    * This handles piece movement, captures, and basic bitboard updates. Note: This is a "raw" move application.
    * Higher-level logic (like switching turns after 3 micro-moves) should be handled by the Turn manager.
    */
  def makeMove(mv: MicroMove): GameState =
    val from        = mv.from
    val to          = mv.to
    val movingPiece = state.mailbox(from)
    val targetPiece = state.mailbox.get(to)

    val fromBB   = Bitboard.fromSquare(from)
    val toBB     = Bitboard.fromSquare(to)
    val fromToBB = fromBB | toBB

    // Update global piece bitboards for active color
    val isWhite  = movingPiece.color.isWhite
    var newWhite = state.whitePieces
    var newBlack = state.blackPieces
    if isWhite then newWhite ^= fromToBB else newBlack ^= fromToBB

    // If it's a capture, remove target piece from opponent's bitboard
    targetPiece.foreach { _ =>
      if isWhite then newBlack &= ~toBB else newWhite &= ~toBB
    }

    // Update piece-type bitboards
    var newPawns   = state.pawns
    var newKnights = state.knights
    var newBishops = state.bishops
    var newRooks   = state.rooks
    var newQueens  = state.queens
    var newKings   = state.kings

    // Remove from 'from' and potentially 'to' (if capture)
    def clear(bb: Bitboard, sqBB: Bitboard): Bitboard = bb & ~sqBB

    // Always clear 'from' for the moving piece type
    movingPiece.pieceType match
      case PieceType.Pawn   => newPawns = clear(newPawns, fromBB)
      case PieceType.Knight => newKnights = clear(newKnights, fromBB)
      case PieceType.Bishop => newBishops = clear(newBishops, fromBB)
      case PieceType.Rook   => newRooks = clear(newRooks, fromBB)
      case PieceType.Queen  => newQueens = clear(newQueens, fromBB)
      case PieceType.King   => newKings = clear(newKings, fromBB)

    // Clear 'to' for target piece type if it was a capture
    targetPiece.foreach { p =>
      p.pieceType match
        case PieceType.Pawn   => newPawns = clear(newPawns, toBB)
        case PieceType.Knight => newKnights = clear(newKnights, toBB)
        case PieceType.Bishop => newBishops = clear(newBishops, toBB)
        case PieceType.Rook   => newRooks = clear(newRooks, toBB)
        case PieceType.Queen  => newQueens = clear(newQueens, toBB)
        case PieceType.King   => newKings = clear(newKings, toBB)
    }

    // Add to 'to' for the moving piece (handling promotion if any)
    val finalPieceType = mv.promotion.getOrElse(movingPiece.pieceType)
    val promoBB        = toBB
    finalPieceType match
      case PieceType.Pawn   => newPawns |= promoBB
      case PieceType.Knight => newKnights |= promoBB
      case PieceType.Bishop => newBishops |= promoBB
      case PieceType.Rook   => newRooks |= promoBB
      case PieceType.Queen  => newQueens |= promoBB
      case PieceType.King   => newKings |= promoBB

    // Update mailbox
    var newMailbox = state.mailbox - from
    newMailbox += (to -> Piece(movingPiece.color, finalPieceType))

    state.copy(
      whitePieces = newWhite,
      blackPieces = newBlack,
      pawns = newPawns,
      knights = newKnights,
      bishops = newBishops,
      rooks = newRooks,
      queens = newQueens,
      kings = newKings,
      mailbox = newMailbox,
      halfMoveClock =
        if movingPiece.pieceType == PieceType.Pawn || targetPiece.isDefined then 0 else state.halfMoveClock + 1
    )

  /** Applies a Move (search-optimized) to the current game state.
    *
    * This handles all special chess rules like castling, en passant, and double pawn pushes using the move's flags.
    */
  @targetName("makeMove_Move")
  def makeMove(mv: Move): GameState = {
    val from        = mv.fromSquare
    val to          = mv.toSquare
    val flags       = mv.flags
    val movingPiece = state.mailbox(from)
    val color       = movingPiece.color
    val isWhite     = color.isWhite

    // 1. Basic bitboard updates
    val fromBB   = Bitboard.fromSquare(from)
    val toBB     = Bitboard.fromSquare(to)
    val fromToBB = fromBB | toBB

    var newWhite = state.whitePieces
    var newBlack = state.blackPieces
    if (isWhite) newWhite ^= fromToBB else newBlack ^= fromToBB

    // 2. Specialized Bitboard & Mailbox Updates
    var newPawns                     = state.pawns
    var newKnights                   = state.knights
    var newBishops                   = state.bishops
    var newRooks                     = state.rooks
    var newQueens                    = state.queens
    var newKings                     = state.kings
    var newMailbox                   = state.mailbox - from
    var newEnPassant: Option[Square] = None
    var newCastlingRights            = state.castlingRights

    // Helper to clear square from all bitboards
    def clearSquare(bb: Bitboard, sqBB: Bitboard): Bitboard = bb & ~sqBB

    // Clear 'from' and 'to' (if capture)
    val targetPiece = if (mv.isCapture && flags != Move.EnPassantCapture) state.mailbox.get(to) else None

    // Always clear from
    movingPiece.pieceType match {
      case PieceType.Pawn   => newPawns = clearSquare(newPawns, fromBB)
      case PieceType.Knight => newKnights = clearSquare(newKnights, fromBB)
      case PieceType.Bishop => newBishops = clearSquare(newBishops, fromBB)
      case PieceType.Rook   => newRooks = clearSquare(newRooks, fromBB)
      case PieceType.Queen  => newQueens = clearSquare(newQueens, fromBB)
      case PieceType.King   => newKings = clearSquare(newKings, fromBB)
      case _                => ()
    }

    // Clear captured piece
    targetPiece.foreach { p =>
      if (isWhite) newBlack = clearSquare(newBlack, toBB) else newWhite = clearSquare(newWhite, toBB)
      p.pieceType match {
        case PieceType.Pawn   => newPawns = clearSquare(newPawns, toBB)
        case PieceType.Knight => newKnights = clearSquare(newKnights, toBB)
        case PieceType.Bishop => newBishops = clearSquare(newBishops, toBB)
        case PieceType.Rook   => newRooks = clearSquare(newRooks, toBB)
        case PieceType.Queen  => newQueens = clearSquare(newQueens, toBB)
        case PieceType.King   => newKings = clearSquare(newKings, toBB)
        case _                => ()
      }
    }

    // Handle special cases
    flags match {
      case Move.DoublePawnPush =>
        newPawns |= toBB
        newMailbox += (to -> Piece(color, PieceType.Pawn))
        newEnPassant = Some(if (isWhite) Square.fromIndex(to.index - 8) else Square.fromIndex(to.index + 8))

      case Move.EnPassantCapture =>
        val victimSq = if (isWhite) Square.fromIndex(to.index - 8) else Square.fromIndex(to.index + 8)
        val victimBB = Bitboard.fromSquare(victimSq)
        if (isWhite) newBlack = clearSquare(newBlack, victimBB) else newWhite = clearSquare(newWhite, victimBB)
        newPawns = clearSquare(newPawns, victimBB)
        newPawns |= toBB
        newMailbox -= victimSq
        newMailbox += (to -> Piece(color, PieceType.Pawn))

      case Move.KingCastle =>
        newKings |= toBB
        newMailbox += (to -> Piece(color, PieceType.King))
        val (rookFrom, rookTo) = if (isWhite) (Square('h', 1), Square('f', 1)) else (Square('h', 8), Square('f', 8))
        val rookBB             = Bitboard.fromSquare(rookFrom) | Bitboard.fromSquare(rookTo)
        if (isWhite) newWhite ^= rookBB else newBlack ^= rookBB
        newRooks ^= rookBB
        newMailbox -= rookFrom
        newMailbox += (rookTo -> Piece(color, PieceType.Rook))

      case Move.QueenCastle =>
        newKings |= toBB
        newMailbox += (to -> Piece(color, PieceType.King))
        val (rookFrom, rookTo) = if (isWhite) (Square('a', 1), Square('d', 1)) else (Square('a', 8), Square('d', 8))
        val rookBB             = Bitboard.fromSquare(rookFrom) | Bitboard.fromSquare(rookTo)
        if (isWhite) newWhite ^= rookBB else newBlack ^= rookBB
        newRooks ^= rookBB
        newMailbox -= rookFrom
        newMailbox += (rookTo -> Piece(color, PieceType.Rook))

      case f if (f & 8) != 0 => // Promotions
        val promType = f match {
          case Move.KnightPromotion | Move.KnightPromoCapture => PieceType.Knight
          case Move.BishopPromotion | Move.BishopPromoCapture => PieceType.Bishop
          case Move.RookPromotion | Move.RookPromoCapture     => PieceType.Rook
          case Move.QueenPromotion | Move.QueenPromoCapture   => PieceType.Queen
          case _                                              => PieceType.Queen
        }
        promType match {
          case PieceType.Knight => newKnights |= toBB
          case PieceType.Bishop => newBishops |= toBB
          case PieceType.Rook   => newRooks |= toBB
          case PieceType.Queen  => newQueens |= toBB
          case _                => ()
        }
        newMailbox += (to -> Piece(color, promType))

      case _ => // Standard Quiet or Capture
        movingPiece.pieceType match {
          case PieceType.Knight => newKnights |= toBB
          case PieceType.Bishop => newBishops |= toBB
          case PieceType.Rook   => newRooks |= toBB
          case PieceType.Queen  => newQueens |= toBB
          case PieceType.King   => newKings |= toBB
          case PieceType.Pawn   => newPawns |= toBB
          case _                => ()
        }
        newMailbox += (to -> Piece(color, movingPiece.pieceType))
    }

    // Update castling rights
    if (movingPiece.pieceType == PieceType.King) {
      if (isWhite) newCastlingRights = newCastlingRights.filterNot(c => c == 'K' || c == 'Q')
      else newCastlingRights = newCastlingRights.filterNot(c => c == 'k' || c == 'q')
    } else if (movingPiece.pieceType == PieceType.Rook) {
      if (isWhite) {
        if (from == Square('a', 1)) newCastlingRights = newCastlingRights.filterNot(_ == 'Q')
        else if (from == Square('h', 1)) newCastlingRights = newCastlingRights.filterNot(_ == 'K')
      } else {
        if (from == Square('a', 8)) newCastlingRights = newCastlingRights.filterNot(_ == 'q')
        else if (from == Square('h', 8)) newCastlingRights = newCastlingRights.filterNot(_ == 'k')
      }
    }
    // Also if we capture a rook!
    targetPiece.foreach { p =>
      if (p.pieceType == PieceType.Rook) {
        if (to == Square('a', 8)) newCastlingRights = newCastlingRights.filterNot(_ == 'q')
        else if (to == Square('h', 8)) newCastlingRights = newCastlingRights.filterNot(_ == 'k')
        else if (to == Square('a', 1)) newCastlingRights = newCastlingRights.filterNot(_ == 'Q')
        else if (to == Square('h', 1)) newCastlingRights = newCastlingRights.filterNot(_ == 'K')
      }
    }
    if (newCastlingRights.isEmpty) newCastlingRights = "-"

    state.copy(
      whitePieces = newWhite,
      blackPieces = newBlack,
      pawns = newPawns,
      knights = newKnights,
      bishops = newBishops,
      rooks = newRooks,
      queens = newQueens,
      kings = newKings,
      mailbox = newMailbox,
      activeColor = state.activeColor.opponent,
      enPassant = newEnPassant,
      castlingRights = newCastlingRights,
      halfMoveClock = if (movingPiece.pieceType == PieceType.Pawn || mv.isCapture) 0 else state.halfMoveClock + 1,
      fullMoveNumber = if (state.activeColor.isBlack) state.fullMoveNumber + 1 else state.fullMoveNumber
    )
  }
