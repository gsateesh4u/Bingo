export type ClaimType =
  | 'ROW'
  | 'COLUMN'
  | 'COLUMN_1'
  | 'COLUMN_2'
  | 'COLUMN_3'
  | 'DIAGONAL'
  | 'FULL_CARD'
  | 'FULL_CARD_FIRST'
  | 'FULL_CARD_SECOND'
  | 'FULL_CARD_THIRD';

export interface Scorecard {
  id: string;
  rows: string[][];
}

export interface Player {
  playerId: string;
  displayName: string;
  scorecard?: Scorecard | null;
}

export interface Winner {
  playerId: string;
  displayName: string;
  claimType: ClaimType;
  timestamp: string;
}

export type GameStatus = 'WAITING_FOR_HOST' | 'IN_PROGRESS' | 'COMPLETE';

export interface GameState {
  status: GameStatus;
  currentCall?: string | null;
  calledPhrases: string[];
  remainingCalls: number;
  playerCount: number;
  winners: Winner[];
}

export interface PlayerResponse extends Player {}
