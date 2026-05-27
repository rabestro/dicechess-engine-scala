/**
 * TypeScript declarations for the Scala.js Dice Chess Engine.
 */
export interface EngineFacadeApi {
    /**
     * Computes a bot move for the given DiceChess FEN (DFEN).
     */
    getBotMove(dfen: string, seed?: number): Record<string, string> | undefined;

    /**
     * Retrieves the dice value (1-6) of the piece at the specified square.
     */
    getPieceTypeAt(dfen: string, square: string): number | undefined;

    /**
     * Applies a move to the given DFEN and returns the resulting state.
     */
    applyMove(dfen: string, from: string, to: string, promotion?: string): string | undefined;
}

export const EngineFacade: EngineFacadeApi;

export interface DiceChessApi {
    /**
     * Returns all legal moves as a flat array of UCI strings (e.g., ["e2e4", "e7e8q"]).
     */
    getLegalUciMoves(dfen: string): string[];

    /**
     * Returns the piece type associated with a dice roll.
     */
    getPieceFromDice(dice: number): string | null;

    /**
     * Computes the best sequence of micro-moves for the given position.
     */
    getBestMove(dfen: string, options?: { algorithm?: string }): { moves: { from: string, to: string, promotion: string | null }[], score: number, timeTakenMs: number };

    /**
     * Applies a move to the given DFEN and returns the resulting state.
     * @param dfen The starting board state in DiceChess FEN notation.
     * @param from The algebraic notation of the starting square.
     * @param to The algebraic notation of the target square.
     * @param promotion The optional piece type to promote to (e.g. "q").
     */
    applyMove(dfen: string, from: string, to: string, promotion?: string): string | undefined;
}

export const DiceChess: DiceChessApi;
