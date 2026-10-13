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
import { ViewTransition, type ReactNode } from 'react';
import { CurrentStep, SCROLLER } from './CurrentStep';
import { ErrorBoundary } from './kit/ErrorBoundary';
import { LiveView } from './LiveView';
import { LOGS_PANEL_ID } from './logsPanel';
import { LogTail } from './LogTail';
import { ConfirmationQuestion } from './prompts/ConfirmationQuestion';
import { HaltOnHover } from './prompts/HaltOnHover';
import type { PromptOfType } from './prompts/Prompt';
import { PromptFrame } from './prompts/PromptFrame';
import { RunHeader, runActivity, type LogsToggle } from './RunHeader';
import { Timeline } from './Timeline';
import type { RunState, SessionInfo } from './types';
import './RunTab.css';

export type RunView = 'step' | 'live';

interface Props {
  state: RunState;
  info: SessionInfo;
  liveViewAllowed: boolean;
  view: RunView;
  onToggleView: () => void;
  logs: LogsToggle & { onClose: () => void };
  onUnauthorized: () => void;
}

/**
 * The run as an app shell whose columns scroll on their own: the timeline on the side, the current step in the middle with the live
 * view as a tile under the timeline, or the live view large in the middle with the step compact under the timeline, and the logs
 * panel when it is open. The live view stays one element whose place changes, so switching never reconnects it.
 */
export function RunTab({ state, info, liveViewAllowed, view, onToggleView, logs, onUnauthorized }: Props) {
  const hasLiveView = info.liveView && liveViewAllowed;
  const live = hasLiveView && view === 'live';
  const confirmation = answeredInTheStep(state);
  const answerInTheAction = confirmation ? (row: ReactNode) => (
    <ErrorBoundary key={confirmation.id}>
      {live ? <HaltOnHover prompt={confirmation} onUnauthorized={onUnauthorized}>{row}</HaltOnHover>
        : <ConfirmationQuestion prompt={confirmation} onUnauthorized={onUnauthorized}>{row}</ConfirmationQuestion>}
    </ErrorBoundary>
  ) : null;
  const framedPrompt = state.prompt && !confirmation && (
    // The prompt arrives with its own animation; a closing one, which useEvents applies in a transition, sinks away
    <ViewTransition key={state.prompt.id} default="none" exit="prompt-leave">
      <div className="run-prompt">
        <ErrorBoundary>
          <PromptFrame prompt={state.prompt} activity={runActivity(state)} searchDelayMillis={info.searchDelayMillis}
            onUnauthorized={onUnauthorized} />
        </ErrorBoundary>
      </div>
    </ViewTransition>
  );
  const classes = ['run', live ? 'run-live-view' : 'run-step-view', hasLiveView ? 'run-has-live' : '', logs.open ? 'run-logs-open' : '']
    .filter(Boolean).join(' ');
  return (
    <div className={classes}>
      <div className="run-head">
        <RunHeader state={state} mode={info.executionMode} logs={logs} />
      </div>
      <div className="run-track">
        <Timeline items={state.items} currentItem={state.currentItem} />
      </div>
      {!live && (
        <div className="run-main">
          {framedPrompt}
          <div className="run-step-scroller" {...{ [SCROLLER]: '' }}>
            <CurrentStep state={state} dense={false} confirmation={answerInTheAction} />
          </div>
        </div>
      )}
      {live && (
        <div className="run-side-step">
          <CurrentStep state={state} dense confirmation={answerInTheAction} />
        </div>
      )}
      {hasLiveView && (
        <div className={live ? 'run-live run-main' : 'run-live'}>
          {live && framedPrompt}
          <LiveView liveControl={info.liveControl} presentation={live ? 'large' : 'tile'} onToggleSize={onToggleView} />
        </div>
      )}
      {logs.open && (
        <aside id={LOGS_PANEL_ID} className="run-logs" aria-label="Agent logs">
          <LogTail lines={state.logTail} firstLine={state.logOffset} onClose={logs.onClose} />
        </aside>
      )}
    </div>
  );
}

/** The confirmation of the executing action, which the step answers instead of a prompt frame. */
function answeredInTheStep(state: RunState): PromptOfType<'confirmation'> | null {
  const { prompt, outline, currentItem } = state;
  if (prompt?.type !== 'confirmation' || outline?.item !== currentItem) {
    return null;
  }
  return outline.procedures.some((node) => node.atomic && node.status === 'RUNNING') ? prompt as PromptOfType<'confirmation'> : null;
}
