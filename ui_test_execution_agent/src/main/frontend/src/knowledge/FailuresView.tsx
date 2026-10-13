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
import { History, LoaderCircle, Map as MapIcon } from 'lucide-react';
import { useState } from 'react';
import { localDateTime } from '../format';
import { Aspect } from '../kit/Aspect';
import { useLoaded } from '../useLoaded';
import { fetchFailures } from './api';
import type { FailureHint, NodeKind } from './model';
import { NodeSearch } from './NodeSearch';
import './FailuresView.css';

const AXIS = { width: 640, row: 26, left: 8, right: 8 };

const PROCEDURE_KINDS: readonly NodeKind[] = ['COMPOSITE', 'ATOMIC'];

interface Props {
  procedureId: string | null;
  onSelectProcedure: (procedureId: string) => void;
  onShowMap: () => void;
  onUnauthorized: () => void;
}

/** The failure hints of one procedure over time: when each last occurred and, for an expired one, when it expired. */
export function FailuresView({ procedureId, onSelectProcedure, onShowMap, onUnauthorized }: Props) {
  const search = (
    <NodeSearch label="Find a procedure" kinds={PROCEDURE_KINDS} onOpen={(hit) => onSelectProcedure(hit.id)} onUnauthorized={onUnauthorized} />
  );
  if (procedureId === null) {
    return (
      <div className="failures card">
        <div className="empty-state">
          <History size={32} />
          <p>Find a procedure to see its failure history, or select one on the map and choose Failure history in its details.</p>
        </div>
        <div className="failures-search">
          {search}
          <button type="button" className="quiet" onClick={onShowMap}><MapIcon size={16} />Go to the map</button>
        </div>
      </div>
    );
  }
  return (
    <div className="failures-page">
      <div className="failures-search card">{search}</div>
      <FailureTimeline key={procedureId} procedureId={procedureId} onUnauthorized={onUnauthorized} />
    </div>
  );
}

function FailureTimeline({ procedureId, onUnauthorized }: { procedureId: string; onUnauthorized: () => void }) {
  const { loaded } = useLoaded(procedureId, (signal) => fetchFailures(procedureId, signal), onUnauthorized);
  const [now] = useState(() => Date.now());

  if (loaded === null) {
    return <p className="loading-note"><LoaderCircle size={18} className="spin" />Loading the failure history…</p>;
  }
  if (loaded.kind !== 'ok') {
    return <p className="alert" role="alert">{loaded.kind === 'not-found' ? 'This procedure no longer exists.' : 'Knowledge graph unavailable.'}</p>;
  }
  const history = loaded.data;
  return (
    <div className="failures card">
      <h3>{history.procedure.description}</h3>
      <p className="section-note">
        Hints that expired more than {history.staleDays} days ago are deleted when the agent starts, so the history reaches back only
        that far past an expiry. Hints carry no first-seen time, so each shows when it last occurred.
      </p>
      {history.hints.length === 0 ? (
        <p className="empty">No failure has been recorded for this procedure.</p>
      ) : (
        <>
          <HintAxis hints={history.hints} now={now} />
          <ul className="hints">
            {history.hints.map((hint) => (
              <li key={hint.id} className={hint.expiredAt ? 'hint hint-expired' : 'hint'}>
                <p className="hint-head">
                  <Aspect tone={hint.expiredAt ? 'neutral' : 'warning'} quiet>{hint.expiredAt ? 'Expired' : 'Current'}</Aspect>
                  <span>{hint.category}</span>
                  <span>{hint.occurrences === 1 ? 'once' : `${hint.occurrences} times`}</span>
                  <span>last {localDateTime(hint.lastOccurred)}</span>
                  {hint.expiredAt && <span>expired {localDateTime(hint.expiredAt)}</span>}
                </p>
                <p>{hint.symptom}</p>
                {hint.resolution.trim() === ''
                  ? <p className="hint-unresolved">Unresolved: nobody has said how to get past it yet.</p>
                  : <p className="hint-resolution">{hint.resolution}</p>}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

function HintAxis({ hints, now }: { hints: FailureHint[]; now: number }) {
  const times = hints.flatMap((hint) => [Date.parse(hint.lastOccurred), hint.expiredAt ? Date.parse(hint.expiredAt) : now]);
  const start = Math.min(...times);
  const span = Math.max(1, now - start);
  const x = (time: number) => AXIS.left + ((time - start) / span) * (AXIS.width - AXIS.left - AXIS.right);
  const height = hints.length * AXIS.row + AXIS.row;
  return (
    <svg className="hint-axis" viewBox={`0 0 ${AXIS.width} ${height}`} role="img" aria-label="When each hint last occurred and expired">
      {hints.map((hint, index) => {
        const y = index * AXIS.row + AXIS.row / 2;
        const occurred = x(Date.parse(hint.lastOccurred));
        const unresolved = hint.resolution.trim() === '';
        return (
          <g key={hint.id} className={hint.expiredAt ? 'hint-mark hint-mark-expired' : 'hint-mark'}>
            <line x1={occurred} x2={hint.expiredAt ? x(Date.parse(hint.expiredAt)) : x(now)} y1={y} y2={y} />
            <circle cx={occurred} cy={y} r={5} className={unresolved ? 'hint-dot-unresolved' : 'hint-dot'}>
              <title>{`${hint.symptom}: last ${localDateTime(hint.lastOccurred)}${unresolved ? ', unresolved' : ''}`}</title>
            </circle>
            {hint.expiredAt && <rect x={x(Date.parse(hint.expiredAt)) - 4} y={y - 4} width={8} height={8} className="hint-expiry" />}
          </g>
        );
      })}
      <text className="trend-axis" x={AXIS.left} y={height - 4}>{localDateTime(new Date(start).toISOString())}</text>
      <text className="trend-axis" x={AXIS.width - AXIS.right} y={height - 4} textAnchor="end">now</text>
    </svg>
  );
}
