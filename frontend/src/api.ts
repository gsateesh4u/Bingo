import { ClaimType, GameState, Player, Scorecard } from './types';

const API_BASE = process.env.REACT_APP_API_BASE ?? 'http://localhost:8080/api';

const hostHeaders = (hostKey?: string) =>
  hostKey
    ? {
        'X-Host-Key': hostKey,
      }
    : undefined;

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options?.headers ?? {}),
    },
    ...options,
  });

  if (!response.ok) {
    const message = await safeMessage(response);
    throw new Error(message);
  }
  if (response.status === 204) {
    return {} as T;
  }
  return (await response.json()) as T;
}

async function safeMessage(response: Response) {
  try {
    const data = await response.json();
    return data.message ?? response.statusText;
  } catch {
    return response.statusText;
  }
}

export const api = {
  createPlayer: (displayName?: string) =>
    request<Player>('/players', {
      method: 'POST',
      body: JSON.stringify({ displayName }),
    }),

  getPlayer: (playerId: string) => request<Player>(`/players/${playerId}`),

  getScorecards: (count = 6) => request<{ scorecards: Scorecard[] }>(`/scorecards?count=${count}`),

  selectScorecard: (playerId: string, scorecardId: string) =>
    request<Player>(`/players/${playerId}/scorecard`, {
      method: 'POST',
      body: JSON.stringify({ scorecardId }),
    }),

  getGameState: () => request<GameState>('/game/state'),

  startGame: (hostKey: string) =>
    request<GameState>('/game/start', {
      method: 'POST',
      headers: hostHeaders(hostKey),
    }),

  drawNumber: (hostKey: string) =>
    request<GameState>('/game/draw', {
      method: 'POST',
      headers: hostHeaders(hostKey),
    }),

  resetGame: (dropPlayers: boolean, hostKey: string) =>
    request<GameState>(`/game/reset?dropPlayers=${dropPlayers}`, {
      method: 'POST',
      headers: hostHeaders(hostKey),
    }),

  claimWin: (playerId: string, claimType: ClaimType) =>
    request<{ accepted: boolean; message: string }>('/game/claim', {
      method: 'POST',
      body: JSON.stringify({ playerId, claimType }),
    }),
};
