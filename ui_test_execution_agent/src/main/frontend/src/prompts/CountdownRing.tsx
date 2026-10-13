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
import './prompts.css';

const RING_RADIUS = 17;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;

/** The share of a countdown still left as a draining ring around the time left; decorative, so the caller states the time as text. */
export function CountdownRing({ fraction, text, urgent }: { fraction: number; text: string; urgent: boolean }) {
  return (
    <span className={urgent ? 'ring ring-urgent' : 'ring'} aria-hidden="true">
      <svg viewBox="0 0 40 40" width="40" height="40">
        <circle className="ring-track" cx="20" cy="20" r={RING_RADIUS} />
        <circle className="ring-fill" cx="20" cy="20" r={RING_RADIUS} strokeDasharray={RING_LENGTH}
          strokeDashoffset={RING_LENGTH * (1 - fraction)} />
      </svg>
      <span className="ring-text">{text}</span>
    </span>
  );
}
