import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from './api';
import {
  CallDetail,
  ClaimType,
  GameState,
  Player,
  PlayerDirectoryEntry,
  Scorecard,
} from './types';
import { ScorecardGrid, FREE_SPACE_TEXT } from './components/ScorecardGrid';
import { NumberWheel } from './components/NumberWheel';
import { useShuffleSound } from './hooks/useShuffleSound';
import './App.css';

type Mode = 'player' | 'host';
const PLAYER_STORAGE_KEY = 'team-bingo-player-id';
const HOST_ACCESS_STORAGE_KEY = 'team-bingo-host-access';
const HOST_ACCESS_KEY = process.env.REACT_APP_HOST_ACCESS_KEY ?? 'TEAM-HOST-KEY';
const HOST_IDENTIFIER = process.env.REACT_APP_HOST_IDENTIFIER ?? 'HOST-LEAD-001';
const HOST_VOICE_STORAGE_KEY = 'team-bingo-host-voice';
const WHEEL_SOUND_STORAGE_KEY = 'team-bingo-wheel-sound';
const CLAIM_OPTIONS: { value: ClaimType; label: string }[] = [
  { value: 'DIAGONAL', label: 'Diagonal' },
  { value: 'COLUMN_1', label: 'First column' },
  { value: 'COLUMN_2', label: 'Second column' },
  { value: 'COLUMN_3', label: 'Third column' },
  { value: 'FULL_CARD_FIRST', label: 'Full card (first winner)' },
  { value: 'FULL_CARD_SECOND', label: 'Full card (second winner)' },
  { value: 'FULL_CARD_THIRD', label: 'Full card (third winner)' },
];
const CLAIM_LABELS: Record<ClaimType, string> = {
  ROW: 'Row',
  COLUMN: 'Column',
  COLUMN_1: 'First column',
  COLUMN_2: 'Second column',
  COLUMN_3: 'Third column',
  DIAGONAL: 'Diagonal',
  FULL_CARD: 'Full card',
  FULL_CARD_FIRST: 'Full card (first winner)',
  FULL_CARD_SECOND: 'Full card (second winner)',
  FULL_CARD_THIRD: 'Full card (third winner)',
};

const formatClaimLabel = (type: ClaimType) => CLAIM_LABELS[type] ?? type.replaceAll('_', ' ');

export default function App() {
  const [mode, setMode] = useState<Mode>('player');
  const [hostSecret, setHostSecret] = useState<string | null>(() => {
    if (typeof window === 'undefined') {
      return null;
    }
    return localStorage.getItem(HOST_ACCESS_STORAGE_KEY);
  });
  const [hostKeyInput, setHostKeyInput] = useState('');
  const [hostKeyError, setHostKeyError] = useState<string | null>(null);
  const hostUnlocked = hostSecret === HOST_ACCESS_KEY;

  const changeMode = (next: Mode) => {
    setMode(next);
    if (next === 'player') {
      setHostKeyError(null);
    }
  };

  const unlockHost = () => {
    const trimmed = hostKeyInput.trim();
    if (trimmed === HOST_ACCESS_KEY) {
        localStorage.setItem(HOST_ACCESS_STORAGE_KEY, trimmed);
        setHostSecret(trimmed);
      setHostKeyInput('');
      setHostKeyError(null);
    } else {
      setHostKeyError('Invalid host key. Check with the event owner and try again.');
    }
  };

  const lockHost = () => {
    localStorage.removeItem(HOST_ACCESS_STORAGE_KEY);
    setHostSecret(null);
    setHostKeyInput('');
  };

  return (
    <div className="app">
      <header className="app__header">
        <div>
          <p className="app__eyebrow app__eyebrow--bright">CETS BINGO</p>
          <h1>Bingo Control Center</h1>
          <p className="app__subtitle">Share this page with teammates to join and play together.</p>
        </div>
        <div className="app__mode-toggle">
          <button className={mode === 'player' ? 'active' : ''} onClick={() => changeMode('player')}>
            Player mode
          </button>
          <button className={mode === 'host' ? 'active' : ''} onClick={() => changeMode('host')}>
            Host mode
          </button>
        </div>
      </header>
      {mode === 'player' ? (
        <PlayerView />
      ) : hostUnlocked && hostSecret ? (
        <HostView hostIdentifier={HOST_IDENTIFIER} hostSecret={hostSecret} onLockHost={lockHost} />
      ) : (
        <HostUnlockPanel
          hostIdentifier={HOST_IDENTIFIER}
          value={hostKeyInput}
          error={hostKeyError}
          onChange={(value) => {
            setHostKeyInput(value);
            setHostKeyError(null);
          }}
          onSubmit={unlockHost}
        />
      )}
    </div>
  );
}

function PlayerView() {
  const [playerId, setPlayerId] = useState<string | null>(() => localStorage.getItem(PLAYER_STORAGE_KEY));
  const [playerIdInput, setPlayerIdInput] = useState(() => localStorage.getItem(PLAYER_STORAGE_KEY) ?? '');
  const [displayNameInput, setDisplayNameInput] = useState('');
  const [player, setPlayer] = useState<Player | null>(null);
  const [availableCards, setAvailableCards] = useState<Scorecard[]>([]);
  const [previewIndex, setPreviewIndex] = useState(0);
  const [gameState, setGameState] = useState<GameState | null>(null);
  const [markedEntries, setMarkedEntries] = useState<Set<string>>(new Set([FREE_SPACE_TEXT]));
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const refreshGameState = useCallback(async () => {
    try {
      const state = await api.getGameState();
      setGameState(state);
    } catch (err) {
      setError((err as Error).message);
    }
  }, []);

  useEffect(() => {
    refreshGameState();
    const interval = setInterval(refreshGameState, 4000);
    return () => clearInterval(interval);
  }, [refreshGameState]);

  useEffect(() => {
    if (!playerId) {
      return;
    }
    let cancelled = false;
    api
      .getPlayer(playerId)
      .then((data) => {
        if (!cancelled) {
          setPlayer(data);
          setMarkedEntries(new Set([FREE_SPACE_TEXT]));
        }
      })
      .catch((err) => setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [playerId]);

  const joinGame = async () => {
    const trimmedId = playerIdInput.trim();
    if (!trimmedId) {
      setError('Enter the player ID provided by your host to continue.');
      return;
    }
    try {
      setLoading(true);
      const created = await api.createPlayer(trimmedId, displayNameInput || undefined);
      localStorage.setItem(PLAYER_STORAGE_KEY, created.playerId);
      setPlayerId(created.playerId);
      setPlayerIdInput(created.playerId);
      setPlayer(created);
      setMessage(`Welcome ${created.displayName}! Pick a scorecard to get started.`);
      setDisplayNameInput('');
      setError(null);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const showCards = async () => {
    if (gameState?.status === 'IN_PROGRESS') {
      setError('Scorecards are locked because the round is in progress.');
      return;
    }
    try {
      const result = await api.getScorecards();
      setAvailableCards(result.scorecards);
      setPreviewIndex(0);
      setMessage('Tap one card to lock it in.');
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const selectCard = async (cardId: string) => {
    if (!playerId) return;
    if (gameState?.status === 'IN_PROGRESS') {
      setError('Scorecards are locked because the round already started.');
      return;
    }
    try {
      const updated = await api.selectScorecard(playerId, cardId);
      setPlayer(updated);
      setAvailableCards([]);
      setPreviewIndex(0);
      setMarkedEntries(new Set([FREE_SPACE_TEXT]));
      setMessage('Card locked in! Wait for host to start the round.');
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const applyPlayerIdInput = () => {
    const trimmed = playerIdInput.trim();
    if (!trimmed) {
      setError('Enter a player ID to load your card.');
      return;
    }
    localStorage.setItem(PLAYER_STORAGE_KEY, trimmed);
    setPlayerId(trimmed);
    setMessage('Player ID updated. Loading your data...');
  };

  const toggleEntry = (value: string) => {
    if (value !== FREE_SPACE_TEXT && !gameState?.calledPhrases?.includes(value)) {
      return;
    }
    setMarkedEntries((prev) => {
      const next = new Set(prev);
      if (next.has(value)) {
        next.delete(value);
      } else {
        next.add(value);
      }
      return next;
    });
  };

  const cyclePreview = (offset: number) => {
    setPreviewIndex((current) => {
      if (availableCards.length === 0) return 0;
      const next = (current + offset + availableCards.length) % availableCards.length;
      return next;
    });
  };

  const currentPreview = availableCards[previewIndex];

  const joined = Boolean(playerId);
  const hasCard = Boolean(player?.scorecard);

  return (
    <section className="panel">
      <div className="panel__header">
        <h2>Player lobby</h2>
        <p>Share the link, everyone joins here, and the host drives the wheel.</p>
      </div>
      {message && (
        <div className="alert alert--info" role="status">
          {message}
        </div>
      )}
      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}
      {!joined && (
        <div className="card">
          <h3>Use your assigned ID</h3>
          <p className="muted">
            The host shared a UUID from the Access roster. Paste it below and add a display name the first time you sign
            in.
          </p>
          <div className="form-row form-row--stack">
            <input
              placeholder="Player ID (UUID)"
              value={playerIdInput}
              onChange={(event) => setPlayerIdInput(event.target.value)}
            />
            <input
              placeholder="Display name (optional)"
              value={displayNameInput}
              onChange={(event) => setDisplayNameInput(event.target.value)}
            />
            <button onClick={joinGame} disabled={loading}>
              Join game
            </button>
          </div>
        </div>
      )}

      {joined && (
        <>
          <div className="card">
            <h3>Your player ID</h3>
            <p className="muted small">
              Keep this ID handy for switching devices or proving your spot in the roster.
            </p>
            <div className="form-row form-row--stack">
              <input
                value={playerIdInput}
                placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000"
                onChange={(event) => setPlayerIdInput(event.target.value)}
              />
              <button onClick={applyPlayerIdInput}>Use this ID</button>
            </div>
            {playerId && (
              <div className="card__row">
                <div>
                  <p className="muted">Active ID</p>
                  <code>{playerId}</code>
                </div>
                <div>
                  <p className="muted">Name</p>
                  <strong>{player?.displayName ?? 'Loading...'}</strong>
                </div>
              </div>
            )}
            <button className="secondary" onClick={showCards} disabled={gameState?.status === 'IN_PROGRESS'}>
              Show random scorecards
            </button>
          </div>

          {availableCards.length > 0 && currentPreview && (
            <div className="card selection-grid">
              <h3>Pick a scorecard</h3>
              <p className="muted">Use the arrows to preview cards, then lock in your favorite.</p>
              <div className="card-carousel">
                <button
                  type="button"
                  className="carousel-btn"
                  onClick={() => cyclePreview(-1)}
                  disabled={availableCards.length <= 1}
                  aria-label="Previous card"
                >
                  ‹
                </button>
                <div className="card-carousel__viewport">
                  <ScorecardGrid card={currentPreview} />
                  <div className="card-carousel__indicator">
                    {previewIndex + 1} / {availableCards.length}
                  </div>
                </div>
                <button
                  type="button"
                  className="carousel-btn"
                  onClick={() => cyclePreview(1)}
                  disabled={availableCards.length <= 1}
                  aria-label="Next card"
                >
                  ›
                </button>
              </div>
              <div className="card-carousel__actions">
                <button onClick={() => selectCard(currentPreview.id)}>Select this card</button>
                <button className="secondary" onClick={showCards}>
                  Refresh cards
                </button>
              </div>
            </div>
          )}

          {hasCard && player?.scorecard && (
            <div className="card">
              <h3>Your card</h3>
              <ScorecardGrid
                card={player.scorecard}
                calledEntries={gameState?.calledPhrases}
                markedEntries={markedEntries}
                onToggle={toggleEntry}
              />
              <p className="muted small">
                Tap a square only after the host calls it&mdash;other squares stay locked. When you spot a winning
                pattern, let the host know so they can verify and record it.
              </p>
            </div>
          )}
        </>
      )}

      <GameStats gameState={gameState} title="Live game feed" />
    </section>
  );
}

interface HostViewProps {
  hostIdentifier: string;
  hostSecret: string;
  onLockHost: () => void;
}

const SPIN_DURATION_MS = 2600;

function HostView({ hostIdentifier, hostSecret, onLockHost }: HostViewProps) {
  const [gameState, setGameState] = useState<GameState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [spinning, setSpinning] = useState(false);
  const [dropPlayers, setDropPlayers] = useState(false);
  const [inspectionId, setInspectionId] = useState('');
  const [inspectedPlayer, setInspectedPlayer] = useState<Player | null>(null);
  const [inspectionMessage, setInspectionMessage] = useState<string | null>(null);
  const [inspectionError, setInspectionError] = useState<string | null>(null);
  const [claimSelection, setClaimSelection] = useState<ClaimType>('DIAGONAL');
  const [playerDirectory, setPlayerDirectory] = useState<PlayerDirectoryEntry[]>([]);
  const [voiceEnabled, setVoiceEnabled] = useState(() => {
    if (typeof window === 'undefined') {
      return true;
    }
    return localStorage.getItem(HOST_VOICE_STORAGE_KEY) !== 'off';
  });
  const [wheelSoundEnabled, setWheelSoundEnabled] = useState(() => {
    if (typeof window === 'undefined') {
      return true;
    }
    return localStorage.getItem(WHEEL_SOUND_STORAGE_KEY) !== 'off';
  });
  const detail = gameState?.currentCallDetail ?? null;
  const [modalDetail, setModalDetail] = useState<CallDetail | null>(null);
  const [secondaryDetail, setSecondaryDetail] = useState<CallDetail | null>(null);
  const spokenRef = useRef<string | null>(null);
  const pendingDetailRef = useRef<CallDetail | null>(null);
  const { playShuffle, stopShuffle } = useShuffleSound();

  const refresh = useCallback(async () => {
    try {
      const state = await api.getGameState();
      setGameState(state);
    } catch (err) {
      setError((err as Error).message);
    }
  }, []);

  const fetchDirectory = useCallback(async () => {
    if (!hostSecret) {
      return;
    }
    try {
      const result = await api.getPlayerDirectory(hostSecret);
      setPlayerDirectory(result.players);
    } catch (err) {
      setInspectionError((err as Error).message);
    }
  }, [hostSecret]);

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 2500);
    return () => clearInterval(interval);
  }, [refresh]);

  useEffect(() => {
    fetchDirectory();
  }, [fetchDirectory]);
  useEffect(() => () => stopShuffle(), [stopShuffle]);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      localStorage.setItem(WHEEL_SOUND_STORAGE_KEY, wheelSoundEnabled ? 'on' : 'off');
    }
    if (!wheelSoundEnabled) {
      stopShuffle();
    }
  }, [wheelSoundEnabled, stopShuffle]);

  const startGame = async () => {
    try {
      const state = await api.startGame(hostSecret);
      setGameState(state);
      setError(null);
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const drawNumber = async () => {
    try {
      setSpinning(true);
      setModalDetail(null);
      pendingDetailRef.current = null;
      if (wheelSoundEnabled) {
        playShuffle(SPIN_DURATION_MS);
      }
      const state = await api.drawNumber(hostSecret);
      setGameState(state);
      pendingDetailRef.current = state.currentCallDetail ?? null;
      setError(null);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      window.setTimeout(() => {
        stopShuffle();
        setSpinning(false);
        if (pendingDetailRef.current) {
          setModalDetail(pendingDetailRef.current);
          pendingDetailRef.current = null;
        }
      }, SPIN_DURATION_MS);
    }
  };

  const resetGame = async () => {
    try {
      const state = await api.resetGame(dropPlayers, hostSecret);
      setGameState(state);
      setError(null);
      setInspectedPlayer(null);
      setInspectionMessage(null);
      setInspectionError(null);
      setClaimSelection('DIAGONAL');
      setInspectionId('');
      setModalDetail(null);
      pendingDetailRef.current = null;
      stopShuffle();
      setSpinning(false);
      fetchDirectory();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const loadPlayerForInspection = async () => {
    if (!inspectionId) {
      setInspectionError('Select a player to inspect.');
      return;
    }
    try {
      const player = await api.getHostPlayer(inspectionId, hostSecret);
      setInspectedPlayer(player);
      setInspectionMessage(`Loaded ${player.displayName}'s card.`);
      setInspectionError(null);
    } catch (err) {
      setInspectionError((err as Error).message);
      setInspectionMessage(null);
      setInspectedPlayer(null);
    }
  };

  const recordClaim = async () => {
    if (!inspectionId) {
      setInspectionError('Load a player before recording a win.');
      return;
    }
    if (!inspectedPlayer?.scorecard) {
      setInspectionError('This player does not have a scorecard yet.');
      return;
    }
    try {
      const result = await api.claimWin(inspectionId, claimSelection, hostSecret);
      setInspectionMessage(result.message);
      setInspectionError(null);
      await refresh();
    } catch (err) {
      setInspectionError((err as Error).message);
    }
  };

  const closeModal = () => {
    setModalDetail(null);
    setSecondaryDetail(null);
  };

  const speechSupported =
    typeof window !== 'undefined' &&
    'speechSynthesis' in window &&
    typeof window.speechSynthesis !== 'undefined' &&
    typeof SpeechSynthesisUtterance !== 'undefined';

  const speakDetail = useCallback(
    (force = false, payload?: CallDetail | null) => {
      const target = payload ?? detail;
      if (!target || !speechSupported || !voiceEnabled) {
        return;
      }
      if (!force && spokenRef.current === target.phrase) {
        return;
      }
      const utterance = new SpeechSynthesisUtterance(`${target.phrase}. ${target.description}`);
      utterance.rate = 1;
      utterance.pitch = 1;
      window.speechSynthesis.cancel();
      window.speechSynthesis.speak(utterance);
      spokenRef.current = target.phrase;
    },
    [detail, speechSupported, voiceEnabled]
  );

  useEffect(() => {
    if (!detail) {
      spokenRef.current = null;
      return;
    }
    speakDetail();
  }, [detail?.phrase, speakDetail]);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      localStorage.setItem(HOST_VOICE_STORAGE_KEY, voiceEnabled ? 'on' : 'off');
    }
    if (!speechSupported) {
      return;
    }
    const activeDetail = modalDetail ?? detail;
    if (!voiceEnabled) {
      window.speechSynthesis.cancel();
    } else if (activeDetail) {
      speakDetail(true, activeDetail);
    }
  }, [voiceEnabled, speechSupported, detail, modalDetail, speakDetail]);

  return (
    <>
      <section className="panel">
        <div className="panel__header">
          <h2>Host controls</h2>
          <p>Launch the wheel, call phrases, and verify every winner against the Access roster.</p>
        </div>
        {error && (
          <div className="alert alert--error" role="alert">
            {error}
          </div>
        )}
        <div className="host-id">
          <div>
            <p className="muted small">Host identifier</p>
            <code>{hostIdentifier}</code>
          </div>
          <button className="ghost" onClick={onLockHost}>
            Use a different host key
          </button>
        </div>
        <div className="host-grid">
          <div className="card host-main">
            <NumberWheel gameState={gameState} spinning={spinning} />
            <div className="audio-toggle">
              <p className="muted small">Wheel sound</p>
              <button
                type="button"
                className="ghost"
                onClick={() => setWheelSoundEnabled((value) => !value)}
              >
                {wheelSoundEnabled ? 'Turn sound off' : 'Turn sound on'}
              </button>
            </div>
            <div className="host-actions">
              <button onClick={startGame}>Start round</button>
              <button onClick={drawNumber} disabled={spinning || gameState?.status !== 'IN_PROGRESS'}>
                Draw next number
              </button>
              <button className="secondary" onClick={resetGame}>
                Reset round
              </button>
            </div>
            <label className="inline-toggle">
              <input
                type="checkbox"
                checked={dropPlayers}
                onChange={(event) => setDropPlayers(event.target.checked)}
              />
              Remove all players on reset
            </label>
            <p className="muted small">
              Start &rarr; Draw to spin phrases. Reset clears the board; optional toggle wipes player cards.
            </p>
          </div>
          <div className="card host-feed">
            <h3>Called phrases ({gameState?.calledPhrases.length ?? 0})</h3>
            <div className="called-grid">
              {gameState && gameState.calledPhrases.length > 0 ? (
                gameState.calledPhrases.map((value) => (
                  <span key={value} className="called-item">
                    <span>{value}</span>
                    <button
                      type="button"
                      className="info-btn"
                      onClick={() =>
                        api
                          .getPhraseDetail(value)
                          .then((detail) => {
                            setSecondaryDetail(detail);
                            setModalDetail({
                              phrase: detail.phrase,
                              title: detail.title,
                              description: detail.description,
                              sourceUrl: detail.sourceUrl ?? undefined,
                            });
                          })
                          .catch((err) => setError((err as Error).message))
                      }
                    >
                      ℹ️
                    </button>
                  </span>
                ))
              ) : (
                <p className="muted">Waiting for the first call...</p>
              )}
            </div>
          </div>
          <div className="card host-inspector">
            <h3>Verify a card</h3>
            <p className="muted small">
              Look up any player to overlay their scorecard with the active call sheet. Pick the correct winning pattern
              and log it for the record.
            </p>
            <div className="form-row form-row--stack">
              <select
                value={inspectionId}
                onChange={(event) => {
                  setInspectionId(event.target.value);
                  setInspectionMessage(null);
                  setInspectionError(null);
                }}
              >
                <option value="">Select a player</option>
                {playerDirectory.map((entry) => {
                  const statusPieces = [
                    entry.joined ? 'joined' : null,
                    entry.hasScorecard ? 'card locked' : null,
                  ].filter(Boolean);
                  const status = statusPieces.length > 0 ? ` (${statusPieces.join(' · ')})` : '';
                  return (
                    <option key={entry.playerId} value={entry.playerId}>
                      {entry.displayName}
                      {status}
                    </option>
                  );
                })}
              </select>
              <button onClick={loadPlayerForInspection} disabled={!inspectionId}>
                Load card
              </button>
              <button type="button" className="ghost" onClick={fetchDirectory}>
                Refresh roster
              </button>
            </div>
            {playerDirectory.length === 0 && (
              <p className="muted small">No players found yet. Refresh after importing the Access roster.</p>
            )}
            {inspectionMessage && (
              <div className="alert alert--info" role="status">
                {inspectionMessage}
              </div>
            )}
            {inspectionError && (
              <div className="alert alert--error" role="alert">
                {inspectionError}
              </div>
            )}
            {inspectedPlayer ? (
              <>
                <div className="host-inspector__meta">
                  <div>
                    <p className="muted small">Player</p>
                    <strong>{inspectedPlayer.displayName}</strong>
                  </div>
                  <div>
                    <p className="muted small">Player ID</p>
                    <code>{inspectedPlayer.playerId}</code>
                  </div>
                </div>
                {inspectedPlayer.scorecard ? (
                  <ScorecardGrid
                    compact
                    card={inspectedPlayer.scorecard}
                    calledEntries={gameState?.calledPhrases}
                  />
                ) : (
                  <p className="muted small">This player has not selected a scorecard yet.</p>
                )}
              </>
            ) : (
              <p className="muted small">Load a player to inspect their scorecard.</p>
            )}
            <div className="host-claim">
              <label>
                <span>Winning pattern</span>
                <select
                  value={claimSelection}
                  onChange={(event) => setClaimSelection(event.target.value as ClaimType)}
                >
                  {CLAIM_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <button onClick={recordClaim} disabled={!inspectedPlayer?.scorecard}>
                Record winner
              </button>
            </div>
          </div>
        </div>
        <GameStats gameState={gameState} title="Round overview" />
      </section>
      {modalDetail && (
        <div className="modal" role="dialog" aria-modal="true" aria-labelledby="callModalTitle">
          <div className="modal__content">
            <button className="modal__close" onClick={closeModal} aria-label="Close call detail popup">
              &times;
            </button>
            <p className="modal__eyebrow">Next square</p>
            <h3 id="callModalTitle">{modalDetail.phrase}</h3>
            <p className="muted">{modalDetail.title}</p>
            <p>{modalDetail.description}</p>
            <div className="modal__actions">
              {modalDetail.sourceUrl && (
                <a href={modalDetail.sourceUrl} target="_blank" rel="noreferrer">
                  Learn more
                </a>
              )}
              <button
                type="button"
                className="ghost"
                onClick={() => setVoiceEnabled((value) => !value)}
                disabled={!speechSupported}
              >
                {speechSupported
                  ? voiceEnabled
                    ? 'Turn voice off'
                    : 'Turn voice on'
                  : 'Voice unavailable'}
              </button>
              <button
                type="button"
                className="ghost"
                onClick={() => speakDetail(true, modalDetail)}
                disabled={!speechSupported || !voiceEnabled}
              >
                Replay narration
              </button>
              <button type="button" onClick={closeModal}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

interface HostUnlockPanelProps {
  hostIdentifier: string;
  value: string;
  error: string | null;
  onChange: (value: string) => void;
  onSubmit: () => void;
}

function HostUnlockPanel({ hostIdentifier, value, error, onChange, onSubmit }: HostUnlockPanelProps) {
  return (
    <section className="panel">
      <div className="panel__header">
        <h2>Host sign in</h2>
        <p>
          Host mode is protected. Enter the private host key to unlock controls for <code>{hostIdentifier}</code>.
        </p>
      </div>
      <div className="card">
        <h3>Host key</h3>
        <p className="muted">Only share this with the meeting facilitator.</p>
        <div className="form-row">
          <input
            type="password"
            placeholder="Enter host key"
            value={value}
            onChange={(event) => onChange(event.target.value)}
          />
          <button onClick={onSubmit}>Unlock host view</button>
        </div>
        {error && (
          <p className="alert alert--error" role="alert">
            {error}
          </p>
        )}
      </div>
    </section>
  );
}

interface GameStatsProps {
  gameState: GameState | null;
  title: string;
}

function GameStats({ gameState, title }: GameStatsProps) {
  const winners = gameState?.winners ?? [];
  const formattedWinners = useMemo(
    () =>
      winners.map((winner) => ({
        ...winner,
        time: new Date(winner.timestamp).toLocaleTimeString(),
        label: formatClaimLabel(winner.claimType),
      })),
    [winners]
  );

  return (
    <div className="card">
      <h3>{title}</h3>
      <div className="stats-grid">
        <div>
          <p className="muted">Status</p>
          <strong>{(gameState?.status ?? 'WAITING_FOR_HOST').replaceAll('_', ' ')}</strong>
        </div>
        <div>
          <p className="muted">Players joined</p>
          <strong>{gameState?.playerCount ?? 0}</strong>
        </div>
        <div>
          <p className="muted">Phrases remaining</p>
          <strong>{gameState?.remainingCalls ?? 0}</strong>
        </div>
      </div>
      <div className="winners">
        <h4>Winners</h4>
        {formattedWinners.length === 0 && <p className="muted">No winners yet.</p>}
        {formattedWinners.length > 0 && (
          <ul>
            {formattedWinners.map((winner) => (
              <li key={`${winner.playerId}-${winner.claimType}-${winner.timestamp}`}>
                <strong>{winner.displayName}</strong> - {winner.label} at {winner.time}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
