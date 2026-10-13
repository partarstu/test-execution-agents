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
import './Meter.css';

export type MeterTone = 'good' | 'fair' | 'poor';

interface Props {
  label: string;
  value: number;
  max: number;
  valueText: string;
}

const GOOD_PERCENT = 80;
const FAIR_PERCENT = 50;

/** The tone of a ratio where more is better, which the bar shows in the status colours next to the number that carries it. */
export function meterTone(percent: number): MeterTone {
  if (percent >= GOOD_PERCENT) {
    return 'good';
  }
  return percent >= FAIR_PERCENT ? 'fair' : 'poor';
}

/** A ratio as a thin bar, announced with its own wording; the caller shows the same wording as text. */
export function Meter({ label, value, max, valueText }: Props) {
  const percent = max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0;
  return (
    <span className={`meter meter-${meterTone(percent)}`} role="meter" aria-label={label} aria-valuemin={0} aria-valuemax={max}
      aria-valuenow={value} aria-valuetext={valueText}>
      <span className="meter-fill" style={{ inlineSize: `${percent}%` }} />
    </span>
  );
}
