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
import { useId, useLayoutEffect, useRef, useState } from 'react';
import './ClampedError.css';

/** An error message cut to its first four lines, with a button to show the whole text when it is longer. */
export function ClampedError({ text }: { text: string }) {
  const body = useRef<HTMLParagraphElement>(null);
  const [clamped, setClamped] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const bodyId = useId();

  useLayoutEffect(() => {
    const element = body.current;
    if (!element || expanded) {
      return;
    }
    const measure = () => setClamped(element.scrollHeight > element.clientHeight);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [text, expanded]);

  return (
    <div className="clamped-error">
      <p ref={body} id={bodyId} className={expanded ? 'clamped-error-text' : 'clamped-error-text clamped-error-collapsed'}>{text}</p>
      {clamped && (
        <button type="button" className="link clamped-error-toggle" aria-expanded={expanded} aria-controls={bodyId}
          onClick={() => setExpanded(!expanded)}>
          {expanded ? 'Show less' : 'Show the whole error'}
        </button>
      )}
    </div>
  );
}
