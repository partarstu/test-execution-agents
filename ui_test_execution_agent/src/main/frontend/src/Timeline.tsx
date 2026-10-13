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
import { Check, ChevronRight, ListChecks, Timer, X } from 'lucide-react';
import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { duration } from './format';
import { ClampedError } from './kit/ClampedError';
import { ResolutionExplanation } from './knowledge/ResolutionExplanation';
import { ITEM_STATUS_LABELS, itemNumber } from './labels';
import type { ItemStatus, TimelineItem } from './types';
import './Timeline.css';

interface Props {
  items: TimelineItem[];
  currentItem: number;
}

const MARKS: Partial<Record<ItemStatus, ReactNode>> = {
  PASSED: <Check size={11} strokeWidth={3.5} />,
  FAILED: <X size={11} strokeWidth={3.5} />,
  ERROR: <span className="rail-mark-text">!</span>,
};

/** The run's preconditions and steps as one track; while it scrolls on its own, the running item stays in view. */
export function Timeline({ items, currentItem }: Props) {
  const track = useRef<HTMLDivElement>(null);
  const headingId = useId();
  const [explained, setExplained] = useState<ReadonlySet<number>>(new Set());

  const toggleExplanation = (index: number) => {
    const next = new Set(explained);
    if (!next.delete(index)) {
      next.add(index);
    }
    setExplained(next);
  };

  useEffect(() => {
    const element = track.current;
    const current = element?.querySelector<HTMLElement>('[aria-current="step"]');
    if (element && current && element.scrollHeight > element.clientHeight) {
      const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
      element.scrollTo({ top: current.offsetTop - element.clientHeight / 3, behavior: reducedMotion ? 'auto' : 'smooth' });
    }
  }, [currentItem]);

  return (
    // Focusable, so a keyboard scrolls the track where it scrolls on its own
    <div ref={track} className="timeline card" role="region" aria-labelledby={headingId} tabIndex={0}>
      <div className="section-head">
        <h2 id={headingId}><ListChecks size={18} />Timeline</h2>
      </div>
      {items.length === 0 ? (
        <p className="empty">The preconditions and steps appear here once a test case starts.</p>
      ) : (
        <ol className="rail">
          {items.map((item, index) => (
            <li
              key={index}
              className={`rail-item rail-item-${item.status.toLowerCase()}`}
              aria-current={index === currentItem ? 'step' : undefined}
            >
              <span className="rail-marker" aria-hidden="true">{MARKS[item.status]}</span>
              <span className="rail-number">{itemNumber(items, index)}</span>
              <div className="rail-body">
                <p className="rail-description">{item.description}</p>
                <div className="rail-meta">
                  <span className="rail-status">{ITEM_STATUS_LABELS[item.status]}</span>
                  {item.resolution && item.status !== 'RUNNING' && item.status !== 'PENDING' && (
                    <span className="rail-duration"><Timer size={14} aria-hidden="true" />{duration(item.resolution.durationMs)}</span>
                  )}
                  {item.resolution && (
                    <button type="button" className="link rail-why-toggle" aria-expanded={explained.has(index)}
                      aria-controls={explained.has(index) ? `${headingId}-why-${index}` : undefined} onClick={() => toggleExplanation(index)}>
                      <ChevronRight size={14} className="disclosure" aria-hidden="true" />
                      Why this procedure
                    </button>
                  )}
                </div>
                {item.errorMessage && <div className="rail-error"><ClampedError text={item.errorMessage} /></div>}
                {item.resolution && explained.has(index) && (
                  <div id={`${headingId}-why-${index}`} className="rail-why">
                    <ResolutionExplanation resolution={item.resolution} />
                  </div>
                )}
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
