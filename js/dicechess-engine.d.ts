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
     * Computes all legal destinations for pieces that match the provided dice rolls.
     */
    getLegalDests(fen: string, diceRolls: number[]): Record<string, string[]> | undefined;

    /**
     * Applies a move to the given FEN and returns the resulting state.
     */
    applyMove(fen: string, from: string, to: string, promotion?: string): string | undefined;
}

export const EngineFacade: EngineFacadeApi;

export interface DiceChessApi {
    /**
     * Returns all legal moves for a given position and a set of available dice rolls.
     */
    getLegalMoves(fen: string, dice: number[]): Record<string, string[]>;

    /**
     * Validates if a move is legal for the given position and any of the available dice rolls.
     */
    isValidMove(fen: string, dice: number[], from: string, to: string): boolean;

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
