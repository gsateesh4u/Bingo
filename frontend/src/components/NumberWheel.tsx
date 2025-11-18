import './NumberWheel.css';
import { GameState } from '../types';

interface NumberWheelProps {
  gameState: GameState | null;
  spinning: boolean;
}

export function NumberWheel({ gameState, spinning }: NumberWheelProps) {
  const status = gameState?.status ?? 'WAITING_FOR_HOST';
  const displayValue = (() => {
    if (spinning) {
      return '...';
    }
    return gameState?.currentCall ?? 'Ready';
  })();
  const statusText = spinning ? 'Spinning...' : status.replaceAll('_', ' ');
  return (
    <div className="wheel">
      <div className={`wheel__rim ${spinning ? 'wheel__rim--spinning' : ''}`}>
        <div className="wheel__value">{displayValue}</div>
        <div className="wheel__status">{statusText}</div>
      </div>
    </div>
  );
}
