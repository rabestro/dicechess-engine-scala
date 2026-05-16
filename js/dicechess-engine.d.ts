/**
 * TypeScript declarations for the Scala.js Dice Chess Engine.
 */
export interface DiceChessApi {
    /**
     * Returns all legal moves for a given position and a set of available dice rolls.
     * @param fen The position in Forsyth-Edwards Notation.
     * @param dice An array of available dice roll results (1-6).
     * @returns A dictionary of legal destination squares grouped by origin square notation.
     */
    getLegalMoves(fen: string, dice: number[]): Record<string, string[]>;

    /**
     * Validates if a move is legal for the given position and any of the available dice rolls.
     * @param fen The position in Forsyth-Edwards Notation.
     * @param dice An array of available dice roll results (1-6).
     * @param from The origin square notation (e.g., "e2").
     * @param to The destination square notation (e.g., "e4").
     * @returns True if the move is legal, false otherwise.
     */
    isValidMove(fen: string, dice: number[], from: string, to: string): boolean;

    /**
     * Returns the piece type associated with a dice roll.
     * @param dice The dice roll (1-6).
     * @returns The piece notation (p, n, b, r, q, k) or null if invalid.
     */
    getPieceFromDice(dice: number): string | null;
}

export const DiceChess: DiceChessApi;
