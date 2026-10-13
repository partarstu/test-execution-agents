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
import { CircleCheck, CircleDashed, Hand, OctagonAlert, SquareX, TriangleAlert, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import type { ItemStatus, RunStatus } from '../types';
import './Aspect.css';

export type Tone = 'passed' | 'failed' | 'warning' | 'error' | 'running' | 'waiting' | 'neutral';

const SHAPES: Record<Exclude<Tone, 'running'>, LucideIcon> = {
  passed: CircleCheck,
  failed: SquareX,
  warning: TriangleAlert,
  waiting: Hand,
  error: OctagonAlert,
  neutral: CircleDashed,
};

/** A status as a signal aspect: a colour, a shape of its own and a word, so it never depends on colour alone. */
export function Aspect({ tone, children, quiet = false }: { tone: Tone; children: ReactNode; quiet?: boolean }) {
  const Shape = tone === 'running' ? null : SHAPES[tone];
  return (
    <span className={`aspect aspect-${tone}${quiet ? ' aspect-quiet' : ''}`}>
      {Shape ? <Shape size={14} strokeWidth={2.25} /> : <span className="aspect-pulse" aria-hidden="true" />}
      {children}
    </span>
  );
}

export const RUN_STATUS_TONES: Record<RunStatus, Tone> = {
  IDLE: 'neutral',
  RUNNING: 'running',
  PASSED: 'passed',
  FAILED: 'failed',
  ERROR: 'error',
  ABORTED: 'neutral',
};

export const ITEM_STATUS_TONES: Record<ItemStatus, Tone> = {
  PENDING: 'neutral',
  RUNNING: 'running',
  PASSED: 'passed',
  FAILED: 'failed',
  ERROR: 'error',
};
