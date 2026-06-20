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

    /**
     * Explicitly ends the current turn, toggling the active color, incrementing full moves,
     * and clearing any stale en-passant targets.
     * @param dfen The current board state in DiceChess FEN notation.
     */
    endTurn(dfen: string): string | undefined;
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

    /**
     * Explicitly ends the current turn, toggling the active color, incrementing full moves,
     * and clearing any stale en-passant targets.
     * @param dfen The current board state in DiceChess FEN notation.
     */
    endTurn(dfen: string): string | undefined;

    /**
     * Determines whether the bot should offer a double before its dice roll.
     */
    shouldBotOfferDouble(dfen: string, currentStake: number, options?: { algorithm?: string }): boolean;

    /**
     * Determines whether the bot should accept (Take) or decline (Drop) a double from the opponent.
     */
    shouldBotAcceptDouble(dfen: string, currentStake: number, options?: { algorithm?: string }): boolean;

    /**
     * Determines whether the bot should offer a draw.
     */
    shouldBotOfferDraw(dfen: string, options?: { algorithm?: string }): boolean;

    /**
     * Determines whether the bot should accept a draw offered by the opponent.
     */
    shouldBotAcceptDraw(dfen: string, options?: { algorithm?: string }): boolean;

    /**
     * Estimates pre-roll equity with a Rao-Blackwellized Monte-Carlo rollout.
     * For progressive use, call in batches (varying `seed`) and pool the per-batch results
     * (`rollouts` is the batch size and `standardError` lets you combine batches).
     * Returns a neutral `undecided = 1` result for an invalid DFEN.
     */
    estimateEquity(dfen: string, options?: { rollouts?: number, maxPlies?: number, seed?: number }): { whiteWin: number, blackWin: number, undecided: number, rollouts: number, standardError: number, varianceReductionVsVanilla: number };

    /**
     * Returns the canonical key (the DFEN of the position's symmetry-class representative),
     * shared by all symmetry-equivalent positions — useful as a cache key. `undefined` for an invalid DFEN.
     */
    canonicalKey(dfen: string): string | undefined;
}

export const DiceChess: DiceChessApi;
