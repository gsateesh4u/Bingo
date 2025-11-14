import './NumberWheel.css';
import { GameState } from '../types';

interface NumberWheelProps {
  gameState: GameState | null;
  spinning: boolean;
}

export function NumberWheel({ gameState, spinning }: NumberWheelProps) {
  const displayValue = gameState?.currentCall ?? 'Ready';
  const status = gameState?.status ?? 'WAITING_FOR_HOST';
  return (
    <div className="wheel">
      <div className={`wheel__rim ${spinning ? 'wheel__rim--spinning' : ''}`}>
        <div className="wheel__value">{displayValue}</div>
        <div className="wheel__status">{status.replaceAll('_', ' ')}</div>
      </div>
    </div>
  );
}
