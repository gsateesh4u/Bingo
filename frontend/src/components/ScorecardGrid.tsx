import { Scorecard } from '../types';
import './ScorecardGrid.css';

export const FREE_SPACE_TEXT = 'FREE SPACE';

interface ScorecardGridProps {
  card: Scorecard;
  calledEntries?: string[];
  markedEntries?: Set<string>;
  onToggle?: (value: string) => void;
  compact?: boolean;
}

const headers = ['B', 'I', 'N', 'G', 'O'];

export function ScorecardGrid({
  card,
  calledEntries = [],
  markedEntries,
  onToggle,
  compact,
}: ScorecardGridProps) {
  const calledSet = new Set(calledEntries);
  const isInteractive = Boolean(onToggle);

  return (
    <div className={`scorecard ${compact ? 'scorecard--compact' : ''}`}>
      {headers.map((label) => (
        <div key={label} className="scorecard__header">
          {label}
        </div>
      ))}
      {card.rows.map((row, rowIndex) =>
        row.map((value, columnIndex) => {
          const key = `${rowIndex}-${columnIndex}-${value}`;
          const isFree = value === FREE_SPACE_TEXT;
          const isCalled = calledSet.has(value) || isFree;
          const marked = markedEntries?.has(value) ?? false;
          const classes = [
            'scorecard__cell',
            isFree ? 'scorecard__cell--free' : '',
            isCalled ? 'scorecard__cell--called' : '',
            marked ? 'scorecard__cell--marked' : '',
            isInteractive ? 'scorecard__cell--interactive' : '',
          ]
            .filter(Boolean)
            .join(' ');
          return (
            <button
              key={key}
              type="button"
              className={classes}
              onClick={() => onToggle?.(value)}
              disabled={!isInteractive}
            >
              <span className="scorecard__cell-text">{isFree ? '★ FREE' : value}</span>
            </button>
          );
        })
      )}
    </div>
  );
}
