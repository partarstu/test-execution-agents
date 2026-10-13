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
import type { NodeFlag, NodeKind } from './model';

/** The panel's design tokens as the canvas-drawn views need them, read from tokens.css so both themes stay in one place. */
export interface ThemeTokens {
  muted: string;
  accent: string;
  kinds: Record<NodeKind, string>;
}

export function readTheme(): ThemeTokens {
  const style = getComputedStyle(document.documentElement);
  const token = (name: string) => style.getPropertyValue(name).trim();
  return {
    muted: token('--muted'),
    accent: token('--accent'),
    kinds: {
      COMPOSITE: token('--kind-composite'),
      ATOMIC: token('--kind-atomic'),
      ELEMENT: token('--kind-element'),
      TEST_CASE: token('--kind-test-case'),
    },
  };
}

export const KIND_LABELS: Record<NodeKind, string> = {
  COMPOSITE: 'Composite procedure',
  ATOMIC: 'Atomic procedure',
  ELEMENT: 'UI element',
  TEST_CASE: 'Test case',
};

export const FLAG_LABELS: Record<NodeFlag, string> = {
  NO_TARGET: 'No target element',
  NO_EFFECTS: 'No effects',
  LOW_STABILITY: 'Low location stability',
  RECENT_ANOMALY: 'Recent anomaly',
};

/** The worst health flag decides a node's ring: a recent anomaly is serious, the others are warnings. */
export function flagSeverity(flags: NodeFlag[]): 'failed' | 'warning' | null {
  if (flags.includes('RECENT_ANOMALY')) {
    return 'failed';
  }
  return flags.length > 0 ? 'warning' : null;
}
