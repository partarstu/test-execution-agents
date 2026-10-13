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
import type { Tab } from './tabs';

export const MOD_KEY = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘' : 'Ctrl';

const VIEW_KEYS: Partial<Record<string, Tab>> = { r: 'run', h: 'health', k: 'knowledge' };
const SEQUENCE_MILLIS = 1_000;

/** Keys pressed together (`+`), one after the other (`then`) or any one of a range (`to`). */
export interface Shortcut {
  keys: string[];
  join: '+' | 'then' | 'to';
  description: string;
}

export const SHORTCUTS: Shortcut[] = [
  { keys: [MOD_KEY, 'K'], join: '+', description: 'Search and jump' },
  { keys: ['G', 'R'], join: 'then', description: 'Go to the run' },
  { keys: ['G', 'H'], join: 'then', description: 'Go to the graph health' },
  { keys: ['G', 'K'], join: 'then', description: 'Go to the knowledge graph' },
  { keys: ['W'], join: '+', description: 'Go to the question the run waits on' },
  { keys: ['L'], join: '+', description: 'Show or hide the agent logs' },
  { keys: ['V'], join: '+', description: 'Open the live view large, or go back to the step' },
  { keys: ['1', '9'], join: 'to', description: 'Choose an answer of the open question by its number' },
  { keys: ['Shift', 'Esc'], join: '+', description: 'Give the keyboard back from the live view' },
  { keys: ['?'], join: '+', description: 'Show these shortcuts' },
];

/** Whether the key belongs to what the operator types or to a control that takes keys itself, so no shortcut may claim it. */
export function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  return target.isContentEditable || target.matches('input, textarea, select') || target.closest('.live-stage') !== null;
}

interface Handlers {
  openPalette: () => void;
  showShortcuts: () => void;
  select: (tab: Tab) => void;
  goToPrompt: (() => void) | null;
  toggleLogs: () => void;
  toggleLiveView: (() => void) | null;
}

/** The panel's global keys; they stay out of fields, the live view and any open dialog, which keep their keys. */
export function useShortcuts(handlers: Handlers): void {
  const sequenceStartedAt = useRef(Number.NEGATIVE_INFINITY);

  const onKeyDown = useEffectEvent((event: KeyboardEvent) => {
    if (event.defaultPrevented || event.altKey || document.querySelector('dialog[open]')) {
      return;
    }
    if ((event.ctrlKey || event.metaKey) && !event.shiftKey && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      handlers.openPalette();
      return;
    }
    if (event.ctrlKey || event.metaKey || isTypingTarget(event.target)) {
      return;
    }
    const key = event.key.toLowerCase();
    if (event.timeStamp - sequenceStartedAt.current < SEQUENCE_MILLIS) {
      sequenceStartedAt.current = Number.NEGATIVE_INFINITY;
      const tab = VIEW_KEYS[key];
      if (tab) {
        event.preventDefault();
        handlers.select(tab);
      }
      return;
    }
    if (key === 'g') {
      sequenceStartedAt.current = event.timeStamp;
    } else if (event.key === '?') {
      event.preventDefault();
      handlers.showShortcuts();
    } else if (key === 'w' && handlers.goToPrompt) {
      event.preventDefault();
      handlers.goToPrompt();
    } else if (key === 'l') {
      event.preventDefault();
      handlers.toggleLogs();
    } else if (key === 'v' && handlers.toggleLiveView) {
      event.preventDefault();
      handlers.toggleLiveView();
    }
  });

  useEffect(() => {
    const listener = (event: KeyboardEvent) => onKeyDown(event);
    document.addEventListener('keydown', listener);
    return () => document.removeEventListener('keydown', listener);
  }, []);
}
