declare module 'dicechess-engine' {
  export interface EngineFacadeApi {
    getBotMove(fen: string, diceRoll: number, seed?: number): Record<string, string> | undefined;
    getPieceTypeAt(fen: string, square: string): number | undefined;
    getLegalDests(fen: string, diceRolls: number[]): Record<string, string[]> | undefined;
    applyMove(fen: string, from: string, to: string, promotion?: string): string | undefined;
  }
  export const EngineFacade: EngineFacadeApi;
}
