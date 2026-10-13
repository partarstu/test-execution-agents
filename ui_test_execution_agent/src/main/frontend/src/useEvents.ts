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
import { startTransition, useEffect, useEffectEvent, useReducer, useState } from 'react';
import { probeSession } from './api';
import { applyEvent, INITIAL_STATE, STATE_EVENT_NAMES, type StateEvent } from './events';
import type { Displaced } from './types';

export type StreamStatus = 'connecting' | 'open' | 'reconnecting';

const RETRY_DELAYS_MS = [1_000, 2_000, 5_000, 10_000];

interface Handlers {
  onDisplaced: (displaced: Displaced) => void;
  onSignedOut: (displaced: Displaced | null) => void;
}

/**
 * Keeps one event stream open while enabled; every (re)connect starts with a snapshot. A failed stream re-probes the session,
 * so a missing login leads to the login view and a restarting server to a retry.
 */
export function useEvents(enabled: boolean, { onDisplaced, onSignedOut }: Handlers) {
  const [state, dispatch] = useReducer(applyEvent, INITIAL_STATE);
  const [streamStatus, setStreamStatus] = useState<StreamStatus>('connecting');
  const handleDisplaced = useEffectEvent(onDisplaced);
  const handleSignedOut = useEffectEvent(onSignedOut);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let source: EventSource | null = null;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;
    let stopped = false;

    const open = () => {
      source = new EventSource('/api/events');
      source.onopen = () => {
        attempt = 0;
        setStreamStatus('open');
      };
      for (const name of STATE_EVENT_NAMES) {
        source.addEventListener(name, (message) => {
          const event = { name, data: JSON.parse(message.data), receivedAt: performance.now() } as StateEvent;
          // As a transition, the closing prompt's <ViewTransition> animates it away; a hidden page would abort the animation
          if (name === 'prompt-closed' && document.visibilityState === 'visible') {
            startTransition(() => dispatch(event));
          } else {
            dispatch(event);
          }
        });
      }
      source.addEventListener('displaced', (message) => {
        stop();
        handleDisplaced(JSON.parse(message.data) as Displaced);
      });
      source.onerror = () => {
        source?.close();
        setStreamStatus('reconnecting');
        void recover();
      };
    };

    const recover = async () => {
      const probe = await probeSession();
      if (stopped) {
        return;
      }
      if (probe.kind === 'none') {
        stop();
        handleSignedOut(probe.displaced);
        return;
      }
      const delay = RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)];
      attempt += 1;
      retryTimer = setTimeout(open, delay);
    };

    const stop = () => {
      stopped = true;
      clearTimeout(retryTimer);
      source?.close();
    };

    open();
    return stop;
  }, [enabled]);

  return { state, streamStatus };
}
