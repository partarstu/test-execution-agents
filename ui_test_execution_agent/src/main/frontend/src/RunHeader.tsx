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
import { Bot, PanelRightClose, ScrollText, UserRoundCheck } from 'lucide-react';
import { useId } from 'react';
import { Aspect, RUN_STATUS_TONES } from './kit/Aspect';
import { ClampedError } from './kit/ClampedError';
import { EXECUTION_MODE_LABELS, RUN_STATUS_LABELS } from './labels';
import { LOGS_PANEL_ID, LOGS_TOGGLE_ID } from './logsPanel';
import { itemCounts, progressText, RunSegments } from './RunProgress';
import type { ExecutionMode, RunState } from './types';
import './RunHeader.css';

export function runActivity(state: RunState): string | null {
  return state.statusMessage ?? state.resultMessage ?? (state.runStatus === 'IDLE' ? 'Waiting for a test case from the orchestrator.' : null);
}

function failedResult(state: RunState): boolean {
  return state.statusMessage === null && (state.runStatus === 'FAILED' || state.runStatus === 'ERROR');
}

export interface LogsToggle {
  open: boolean;
  /** The warnings and errors that arrived while the logs were closed. */
  unseen: number;
  onToggle: () => void;
}

interface Props {
  state: RunState;
  mode: ExecutionMode;
  logs: LogsToggle;
}

/**
 * The run at a glance in one row on a wide tab: the test case, how the agent runs it, its status and where it is, with what it is
 * doing now while no step shows it, and the toggle of the logs panel.
 */
export function RunHeader({ state, mode, logs }: Props) {
  const headingId = useId();
  const ModeIcon = mode === 'SUPERVISED' ? UserRoundCheck : Bot;
  const activity = state.runStatus === 'RUNNING' ? null : runActivity(state);
  const testCase = state.testCaseName ?? 'No test case yet';
  const counts = itemCounts(state.items);
  const progress = progressText(counts);
  return (
    <section className={`run-header on-stage run-header-${state.runStatus.toLowerCase()}`} aria-labelledby={headingId}>
      <div className="run-header-top">
        <div className="run-header-title">
          <p className="eyebrow">Test case</p>
          <h2 id={headingId} title={testCase}>{testCase}</h2>
        </div>
        <div className="run-header-meta">
          <span className="mode-chip"><ModeIcon size={16} />{EXECUTION_MODE_LABELS[mode]}</span>
          <Aspect tone={RUN_STATUS_TONES[state.runStatus]}>{RUN_STATUS_LABELS[state.runStatus]}</Aspect>
          <button type="button" id={LOGS_TOGGLE_ID} className={logs.open ? 'small primary logs-toggle' : 'small logs-toggle'}
            aria-expanded={logs.open} aria-controls={logs.open ? LOGS_PANEL_ID : undefined} aria-keyshortcuts="L" onClick={logs.onToggle}>
            {logs.open ? <PanelRightClose size={16} /> : <ScrollText size={16} />}
            {logs.open ? 'Hide logs' : 'Logs'}
            {!logs.open && logs.unseen > 0 && (
              <>
                <span className="logs-badge" aria-hidden="true">{logs.unseen > 99 ? '99+' : logs.unseen}</span>
                <span className="visually-hidden">, {logs.unseen === 1 ? '1 new warning or error' : `${logs.unseen} new warnings or errors`}</span>
              </>
            )}
            <kbd className="kbd" aria-hidden="true">L</kbd>
          </button>
        </div>
      </div>
      {state.items.length > 0 && (
        <div className="run-header-progress" title={progress}>
          <RunSegments items={state.items} currentItem={state.runStatus === 'RUNNING' ? state.currentItem : -1} />
          <p className="run-header-counts">
            <span aria-hidden="true">{state.items.length - counts.running - counts.pending} of {state.items.length} done</span>
            <span className="visually-hidden">{progress}</span>
          </p>
        </div>
      )}
      {activity && (failedResult(state) ? (
        <div className="run-activity"><ClampedError text={activity} /></div>
      ) : (
        <p className="run-activity">
          {state.runStatus === 'RUNNING' && state.statusMessage && <span className="run-activity-dot" aria-hidden="true" />}
          {activity}
        </p>
      ))}
    </section>
  );
}
