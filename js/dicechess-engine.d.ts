/**
 * TypeScript declarations for the Scala.js Dice Chess Engine.
 */
export interface EngineFacadeApi {
    /**
     * Computes a bot move for the given FEN and dice roll.
     */
    getBotMove(fen: string, diceRoll: number, seed?: number): Record<string, string> | undefined;

    /**
     * Retrieves the dice value (1-6) of the piece at the specified square.
     */
    getPieceTypeAt(fen: string, square: string): number | undefined;

    /**
     * Applies a move to the given FEN and returns the resulting state.
     */
    applyMove(fen: string, from: string, to: string, promotion?: string): string | undefined;
}

export const EngineFacade: EngineFacadeApi;

export interface DiceChessApi {
    /**
     * Returns all legal moves as a flat array of UCI strings (e.g., ["e2e4", "e7e8q"]).
     */
    getLegalUciMoves(fen: string, dice: number[]): string[];

    /**
     * Returns the piece type associated with a dice roll.
     */
    getPieceFromDice(dice: number): string | null;

    /**
     * Computes the best sequence of micro-moves for the given position and dice.
     */
    getBestMove(fen: string, dice: number[], options?: { algorithm?: string }): { moves: { from: string, to: string, promotion: string | null }[], score: number, timeTakenMs: number };

    /**
     * Applies a move to the given FEN and returns the resulting state.
     * @param fen The starting board state in FEN notation.
     * @param from The algebraic notation of the starting square.
     * @param to The algebraic notation of the target square.
     * @param promotion The optional piece type to promote to (e.g. "q").
     */
    applyMove(fen: string, from: string, to: string, promotion?: string): string | undefined;
}

export const DiceChess: DiceChessApi;
