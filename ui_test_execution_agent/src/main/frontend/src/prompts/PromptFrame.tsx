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
import {
  CircleAlert, CirclePlay, Info, ListStart, NotebookPen, OctagonAlert, PencilRuler, SearchX, Signpost, TriangleAlert,
} from 'lucide-react';
import { useId, useState, type KeyboardEvent, type ReactElement } from 'react';
import { clockTime } from '../format';
import { Modal } from '../kit/Modal';
import { PROMPT_ANCHOR_ID } from './anchor';
import { ConfirmationBody } from './bodies/ConfirmationBody';
import { FailureContextBody } from './bodies/FailureContextBody';
import { MessageBody } from './bodies/MessageBody';
import { NextActionBody } from './bodies/NextActionBody';
import { StartStepBody } from './bodies/StartStepBody';
import { VerificationFailureBody } from './bodies/VerificationFailureBody';
import { countdownState, formatCompact, formatRemaining, openedAtOf, useRemainingMillis } from './countdown';
import { CountdownRing } from './CountdownRing';
import { ProcedureEditor } from './editor/ProcedureEditor';
import type { ReceivedPrompt } from './Prompt';
import { promptTitle } from './titles';
import { useAnswer, type PromptCalls } from './useAnswer';
import './prompts.css';

interface Props {
  prompt: ReceivedPrompt;
  /** What the agent last reported doing, which the run header leaves out while the prompt is open. */
  activity: string | null;
  searchDelayMillis: number;
  onUnauthorized: () => void;
}

/**
 * What every prompt shares, as a modal when Escape has a safe answer: its icon and title, the waiting state with the time left and
 * the default that then applies, the pending state of a call and the server's rejection. The pointer on the prompt pauses its
 * countdown, and moving the keyboard focus through it holds the countdown for good.
 */
export function PromptFrame({ prompt, activity, searchDelayMillis, onUnauthorized }: Props) {
  const calls = useAnswer(prompt.id, onUnauthorized);
  const [openedAt] = useState(() => openedAtOf(prompt, Date.now(), performance.now()));
  const titleId = useId();
  const escapeAnswer = safeAnswerOf(prompt);
  // The listeners only watch where the operator is to pause or hold the countdown; they give the section no control to operate
  const content = (
    <section id={PROMPT_ANCHOR_ID} tabIndex={-1} className={`prompt prompt-${prompt.type}`} aria-labelledby={titleId}
      aria-busy={calls.pending} {...countdownListeners(prompt, calls)}>
      <div className="prompt-head">
        <span className="prompt-icon" aria-hidden="true"><PromptIcon prompt={prompt} /></span>
        <div className="prompt-heading">
          <p className="eyebrow">The run waits for you</p>
          <h2 id={titleId} className="prompt-title">{promptTitle(prompt)}</h2>
          {activity && <p className="prompt-activity">{activity}</p>}
        </div>
      </div>
      <WaitingState prompt={prompt} openedAt={openedAt} />
      {bodyOf(prompt, calls, searchDelayMillis)}
      {calls.error && <p className="prompt-error" role="alert"><CircleAlert size={18} />{calls.error}</p>}
    </section>
  );
  return escapeAnswer
    ? <Modal open labelledBy={titleId} onCancel={() => calls.answer(escapeAnswer)} wide seeThrough>{content}</Modal>
    : content;
}

/** The pointer on a question pauses its countdown, and Tab inside it holds the countdown for good. */
export function countdownListeners(prompt: ReceivedPrompt, calls: PromptCalls) {
  if (!prompt.countdown || prompt.held) {
    return {};
  }
  return {
    onPointerEnter: calls.pause,
    onPointerLeave: calls.resume,
    onKeyDown: (event: KeyboardEvent) => {
      if (event.key === 'Tab') {
        calls.hold();
      }
    },
  };
}

function bodyOf(prompt: ReceivedPrompt, calls: PromptCalls, searchDelayMillis: number): ReactElement {
  const shared = { answer: calls.answer, call: calls.call, hold: calls.hold, pending: calls.pending, searchDelayMillis };
  switch (prompt.type) {
    case 'start-step':
      return <StartStepBody prompt={prompt} {...shared} />;
    case 'confirmation':
      return <ConfirmationBody prompt={prompt} {...shared} />;
    case 'next-action':
      return <NextActionBody prompt={prompt} {...shared} />;
    case 'message':
      return <MessageBody prompt={prompt} {...shared} />;
    case 'verification-failure':
      return <VerificationFailureBody prompt={prompt} {...shared} />;
    case 'failure-context':
      return <FailureContextBody prompt={prompt} {...shared} />;
    case 'procedure-editor':
      return <ProcedureEditor prompt={prompt} {...shared} />;
  }
}

const ICON_SIZE = 22;

function PromptIcon({ prompt }: { prompt: ReceivedPrompt }) {
  switch (prompt.type) {
    case 'start-step':
      return <ListStart size={ICON_SIZE} />;
    case 'confirmation':
      return <CirclePlay size={ICON_SIZE} />;
    case 'next-action':
      return <Signpost size={ICON_SIZE} />;
    case 'message':
      if (prompt.payload.severity === 'ERROR') {
        return <OctagonAlert size={ICON_SIZE} />;
      }
      return prompt.payload.severity === 'WARNING' ? <TriangleAlert size={ICON_SIZE} /> : <Info size={ICON_SIZE} />;
    case 'verification-failure':
      return <SearchX size={ICON_SIZE} />;
    case 'failure-context':
      return <NotebookPen size={ICON_SIZE} />;
    case 'procedure-editor':
      return <PencilRuler size={ICON_SIZE} />;
  }
}

/** The answer Escape gives a modal prompt, which is its default; the inline prompts have none. */
function safeAnswerOf(prompt: ReceivedPrompt): object | null {
  switch (prompt.type) {
    case 'failure-context':
      return { choice: 'DISMISS' };
    default:
      return null;
  }
}

function defaultOf(prompt: ReceivedPrompt): string | null {
  switch (prompt.type) {
    case 'confirmation':
      return prompt.payload.beforeExecution ? 'The action proceeds' : 'The run goes on';
    case 'message':
    case 'verification-failure':
      return 'The run continues';
    case 'failure-context':
      return 'It closes without a note';
    default:
      return null;
  }
}

/** How long the prompt still waits and what then happens, as one sentence. */
export function waitingOutcome(prompt: ReceivedPrompt, remaining: number): string {
  const { counting, paused } = countdownState(prompt, remaining);
  const defaultText = prompt.hasDefault ? defaultOf(prompt) : null;
  const left = paused ? `${formatRemaining(remaining)} once the pointer leaves the prompt` : formatRemaining(remaining);
  if (defaultText === null) {
    return `If nobody answers within ${left}, the run fails.`;
  }
  return counting ? `${defaultText} in ${left}.` : `${defaultText} in ${left} unless you answer.`;
}

/**
 * The waiting state: since when the run waits, how long it still waits and what then happens, with a draining fuse and a ring
 * for a countdown; it re-renders on its own as the time runs, so the prompt body does not.
 */
export function WaitingState({ prompt, openedAt }: { prompt: ReceivedPrompt; openedAt: Date }) {
  const remaining = useRemainingMillis(prompt);
  const { counting, urgent, fraction } = countdownState(prompt, remaining);
  const outcome = waitingOutcome(prompt, remaining);
  return (
    <div className={urgent ? 'waiting waiting-urgent' : 'waiting'}>
      {counting && (
        <div className={urgent ? 'fuse fuse-urgent' : 'fuse'} aria-hidden="true">
          <span style={{ transform: `scaleX(${fraction})` }} />
        </div>
      )}
      {counting && <CountdownRing fraction={fraction} text={formatCompact(remaining)} urgent={urgent} />}
      <p role="timer">
        <span className="waiting-since">Waiting since {clockTime(openedAt)}</span>
        <strong>{outcome}</strong>
      </p>
    </div>
  );
}
