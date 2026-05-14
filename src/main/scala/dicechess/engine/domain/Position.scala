package dicechess.engine.domain

import dicechess.engine.domain.*

extension (state: GameState)
  /** Applies a micro-move to the current game state. This handles piece movement, captures, and basic bitboard updates.
    *
    * Note: This is a "raw" move application. Higher-level logic (like switching turns after 3 micro-moves) should be
    * handled by the Turn manager.
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
      // For now, we don't switch colors automatically in micro-move application
      // unless it was a king capture (game over) - but we'll handle turn logic later.
      halfMoveClock =
        if movingPiece.pieceType == PieceType.Pawn || targetPiece.isDefined then 0 else state.halfMoveClock + 1
    )
