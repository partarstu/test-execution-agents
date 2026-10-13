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
import { useEffect, useEffectEvent, useRef } from 'react';
import faviconUrl from './assets/favicon.svg';
import waitingFaviconUrl from './assets/favicon-waiting.svg';
import { ITEM_STATUS_LABELS, itemName, RUN_STATUS_LABELS } from './labels';
import type { PromptView } from './prompts/Prompt';
import { promptTitle } from './prompts/titles';
import type { RunState, RunStatus } from './types';

const APP_NAME = 'Operator panel';
const NOTIFY_KEY = 'panel.notify';

interface Watched {
  prompt: PromptView | null;
  testCaseName: string | null;
  runStatus: RunStatus;
}

/** The browser tab title, which leads with the question while the run waits, so a background tab shows it in the tab strip. */
export function documentTitle({ prompt, testCaseName, runStatus }: Watched): string {
  if (prompt) {
    return `Waiting for you: ${promptTitle(prompt)} – ${APP_NAME}`;
  }
  return testCaseName ? `${RUN_STATUS_LABELS[runStatus]}: ${testCaseName} – ${APP_NAME}` : APP_NAME;
}

export function promptAnnouncement(prompt: PromptView | null): string {
  return prompt ? `The run waits for your answer: ${promptTitle(prompt)}` : '';
}

const RUN_END_ANNOUNCEMENTS: Record<Exclude<RunStatus, 'IDLE' | 'RUNNING'>, string> = {
  PASSED: 'The run passed',
  FAILED: 'The run failed',
  ERROR: 'The run ended with an error',
  ABORTED: 'The run was aborted',
};

/**
 * What a screen reader hears about the run: its start, each item's start and end and the run's end, never the agent's
 * moment-to-moment activity, which would talk over everything else.
 */
export function runAnnouncement({ runStatus, testCaseName, items, currentItem, resultMessage }: RunState): string {
  if (runStatus === 'IDLE' || testCaseName === null) {
    return '';
  }
  if (runStatus !== 'RUNNING') {
    return resultMessage ? `${RUN_END_ANNOUNCEMENTS[runStatus]}: ${resultMessage}` : RUN_END_ANNOUNCEMENTS[runStatus];
  }
  const item = items[currentItem];
  if (item === undefined) {
    return `The run started: ${testCaseName}`;
  }
  return `${itemName(items, currentItem)}, ${ITEM_STATUS_LABELS[item.status].toLowerCase()}: ${item.description}`;
}

export const notificationsSupported = () => typeof Notification === 'function' && isSecureContext;

export function storedNotify(): boolean {
  try {
    return localStorage.getItem(NOTIFY_KEY) === 'on' && notificationsSupported() && Notification.permission === 'granted';
  } catch {
    return false;
  }
}

/** Turns desktop notifications on, asking the browser first; resolves with whether they are on. */
export async function enableNotify(enabled: boolean): Promise<boolean> {
  const granted = enabled && (Notification.permission === 'granted' || (await Notification.requestPermission()) === 'granted');
  try {
    if (granted) {
      localStorage.setItem(NOTIFY_KEY, 'on');
    } else {
      localStorage.removeItem(NOTIFY_KEY);
    }
  } catch {
    // Blocked storage only means the choice lasts until the page is reloaded
  }
  return granted;
}

/** Keeps the tab title and the favicon in step with the run and, when asked for, notifies each new prompt the operator is not looking at. */
export function useAttention(watched: Watched, notify: boolean): void {
  const { prompt } = watched;
  const title = documentTitle(watched);
  const notifiedPromptId = useRef<string | null>(null);
  const shouldNotify = useEffectEvent(() => notify && (document.visibilityState === 'hidden' || !document.hasFocus()));

  useEffect(() => {
    document.title = title;
  }, [title]);

  useEffect(() => {
    faviconLink().href = prompt ? waitingFaviconUrl : faviconUrl;
  }, [prompt]);

  useEffect(() => {
    if (!prompt || notifiedPromptId.current === prompt.id || !shouldNotify()) {
      return;
    }
    notifiedPromptId.current = prompt.id;
    const notification = new Notification('The run waits for you', { body: promptTitle(prompt), tag: 'operator-panel-prompt', icon: waitingFaviconUrl });
    notification.onclick = () => {
      window.focus();
      notification.close();
    };
  }, [prompt]);
}

function faviconLink(): HTMLLinkElement {
  let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
  if (!link) {
    link = document.createElement('link');
    link.rel = 'icon';
    link.type = 'image/svg+xml';
    document.head.append(link);
  }
  return link;
}
