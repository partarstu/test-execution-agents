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
import { useState } from 'react';
import { useTooltip } from './Tooltip';
import './Screenshot.css';

interface Props {
  src: string;
  alt: string;
  size?: 'thumb' | 'card' | 'full';
}

/** A screenshot shown at a readable size that opens at its natural size on click. */
export function Screenshot({ src, alt, size = 'full' }: Props) {
  const [zoomed, setZoomed] = useState(false);
  const { trigger, tooltip } = useTooltip(zoomed ? 'Show smaller' : 'Show at full size');
  return (
    <>
      <button
        type="button"
        className={`screenshot screenshot-${size}${zoomed ? ' screenshot-zoomed' : ''}`}
        onClick={() => setZoomed(!zoomed)}
        aria-pressed={zoomed}
        {...trigger}
      >
        <img src={src} alt={alt} loading="lazy" />
      </button>
      {tooltip}
    </>
  );
}
