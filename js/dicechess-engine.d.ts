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

