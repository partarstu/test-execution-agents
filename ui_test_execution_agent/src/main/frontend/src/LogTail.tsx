/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
 * Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import { ArrowUpToLine, Check, CircleAlert, Copy, ScrollText, WrapText, X, type LucideIcon } from 'lucide-react';
import { useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { COPY_BLOCKED, copyText } from './clipboard';
import { IconButton } from './kit/IconButton';
import './LogTail.css';

const STICK_TO_TOP_PX = 24;
const COPY_RESULT_MILLIS = 4_000;

type CopyResult = 'copied' | 'blocked';

const COPY_RESULT_ICONS: Record<CopyResult, LucideIcon> = { copied: Check, blocked: CircleAlert };

export type LogLevel = 'error' | 'warn' | 'other';

type Filter = 'all' | 'warn' | 'error';

const FILTERS: { id: Filter; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'warn', label: 'Warnings' },
  { id: 'error', label: 'Errors' },
];

export function levelOf(line: string): LogLevel {
  const level = /\b(ERROR|FATAL|SEVERE|WARN|WARNING)\b/.exec(line)?.[1];
  if (level === undefined) {
    return 'other';
  }
  return level.startsWith('WARN') ? 'warn' : 'error';
}

export interface LineParts {
  time: string;
  level: string;
  logger: string;
  message: string;
}

/** The parts of a line in the agent's format, `HH:mm:ss.SSS LEVEL logger - message`, or null for a line in any other shape. */
export function lineParts(line: string): LineParts | null {
  const match = /^(\d{2}:\d{2}:\d{2}\.\d{3}) (\S+)\s+(\S+) - ([\s\S]*)$/.exec(line);
  return match ? { time: match[1], level: match[2], logger: match[3], message: match[4] } : null;
}

/** The text with every case-insensitive occurrence of the query marked. */
export function highlighted(text: string, query: string): ReactNode {
  const needle = query.trim().toLowerCase();
  if (needle === '') {
    return text;
  }
  const parts: ReactNode[] = [];
  const lower = text.toLowerCase();
  let from = 0;
  for (let at = lower.indexOf(needle); at >= 0; at = lower.indexOf(needle, at + needle.length)) {
    parts.push(text.slice(from, at), <mark key={at}>{text.slice(at, at + needle.length)}</mark>);
    from = at + needle.length;
  }
  parts.push(text.slice(from));
  return parts;
}

/**
 * The newest log lines of the run, newest first, following the newest until the operator scrolls down to read, filtered by level or text;
 * `firstLine` numbers the oldest line given, so a line keeps its key while older ones drop off.
 */
export function LogTail({ lines, firstLine, onClose }: { lines: string[]; firstLine: number; onClose: () => void }) {
  const viewport = useRef<HTMLDivElement>(null);
  const [following, setFollowing] = useState(true);
  const [copyResult, setCopyResult] = useState<CopyResult | null>(null);
  const [filter, setFilter] = useState<Filter>('all');
  const [query, setQuery] = useState('');
  const [wrap, setWrap] = useState(true);
  const headingId = useId();

  const levels = lines.map(levelOf);
  const counts = { all: lines.length, warn: levels.filter((level) => level === 'warn').length, error: levels.filter((level) => level === 'error').length };
  const needle = query.trim().toLowerCase();
  const shown = lines
    .map((line, index) => ({ line, level: levels[index], key: firstLine + index }))
    .filter(({ line, level }) => (filter === 'all' || level === filter) && (needle === '' || line.toLowerCase().includes(needle)));

  useLayoutEffect(() => {
    const element = viewport.current;
    if (element && following) {
      element.scrollTop = 0;
    }
  }, [lines, following, filter, query]);

  useEffect(() => {
    if (copyResult === null) {
      return;
    }
    const timer = setTimeout(() => setCopyResult(null), COPY_RESULT_MILLIS);
    return () => clearTimeout(timer);
  }, [copyResult]);

  // An operator who scrolled down to read is not pulled back up by new lines; the browser's scroll anchoring keeps their place
  const onScroll = () => {
    const element = viewport.current;
    if (element) {
      setFollowing(element.scrollTop < STICK_TO_TOP_PX);
    }
  };

  const copy = async () => {
    const copied = await copyText(shown.map(({ line }) => line).join('\n'));
    setCopyResult(copied ? 'copied' : 'blocked');
  };

  const filtered = filter !== 'all' || needle !== '';

  return (
    <section className="log card">
      <div className="section-head">
        <h2 id={headingId}><ScrollText size={18} />Agent logs</h2>
        <span className="section-note">
          {filtered ? `${shown.length} of ${lines.length} lines` : lines.length === 1 ? '1 line' : `${lines.length} lines`}
        </span>
        <div className="section-tools">
          <span className={copyResult === 'blocked' ? 'log-copy-result log-copy-blocked' : 'log-copy-result'} role="status">
            {copyResult === 'copied' && 'Copied'}
            {copyResult === 'blocked' && COPY_BLOCKED}
          </span>
          <IconButton icon={copyResult ? COPY_RESULT_ICONS[copyResult] : Copy}
            label={filtered ? 'Copy the lines shown' : 'Copy the log'} small disabled={shown.length === 0} onClick={() => void copy()} />
          <IconButton icon={X} label="Close the logs" small onClick={onClose} />
        </div>
      </div>
      <div className="log-toolbar">
        <fieldset className="segmented">
          <legend className="visually-hidden">Show</legend>
          {FILTERS.map(({ id, label }) => (
            <label key={id} className={`segment segment-${id}`}>
              <input type="radio" name={`${headingId}-level`} checked={filter === id} onChange={() => setFilter(id)} />
              {label}{' '}
              <span className="segment-count">{counts[id]}</span>
            </label>
          ))}
        </fieldset>
        <input type="search" className="log-search" aria-label="Find in the log" placeholder="Find in the log" maxLength={200}
          value={query} onChange={(event) => setQuery(event.target.value)} />
        <IconButton icon={WrapText} label="Wrap long lines" small aria-pressed={wrap} onClick={() => setWrap(!wrap)} />
      </div>
      <div className="log-frame">
        {/* A focusable region rather than role="log", whose implied live announcements would read out every streamed line */}
        <div ref={viewport} className={wrap ? 'log-lines' : 'log-lines log-lines-nowrap'} role="region" aria-labelledby={headingId}
          tabIndex={0} onScroll={onScroll}>
          {shown.length === 0
            ? <p className="empty">{lines.length === 0 ? 'No log lines yet.' : 'No line matches.'}</p>
            : shown.toReversed().map(({ line, level, key }) => <LogLine key={key} line={line} level={level} query={query} />)}
        </div>
        {!following && (
          <button type="button" className="small primary log-jump" onClick={() => setFollowing(true)}>
            <ArrowUpToLine size={16} />
            Jump to the newest
          </button>
        )}
      </div>
    </section>
  );
}

function LogLine({ line, level, query }: { line: string; level: LogLevel; query: string }) {
  const parts = lineParts(line);
  if (parts === null) {
    return <div className={`log-line log-${level}`}>{highlighted(line, query)}</div>;
  }
  return (
    <div className={`log-line log-${level}`}>
      <span className="log-time">{parts.time}</span>{' '}
      <span className="log-level">{parts.level.padEnd(5)}</span>{' '}
      <span className="log-logger">{highlighted(parts.logger, query)}</span>{' - '}
      {highlighted(parts.message, query)}
    </div>
  );
}
