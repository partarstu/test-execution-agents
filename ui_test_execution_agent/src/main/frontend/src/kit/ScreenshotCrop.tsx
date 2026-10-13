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
import { useRef, useState, type PointerEvent } from 'react';
import './ScreenshotCrop.css';

export interface Area {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface Props {
  src: string;
  /** The capture's size in pixels, in which the selected area is reported. */
  width: number;
  height: number;
  area: Area | null;
  onSelect: (area: Area | null) => void;
}

/** A screen capture on which the operator drags a rectangle, or types its position, in the capture's own pixels. */
export function ScreenshotCrop({ src, width, height, area, onSelect }: Props) {
  const image = useRef<HTMLImageElement>(null);
  const [start, setStart] = useState<{ x: number; y: number } | null>(null);

  const toPixels = (event: PointerEvent<HTMLElement>) => {
    const box = image.current!.getBoundingClientRect();
    const scale = width / box.width;
    return {
      x: clamp(Math.round((event.clientX - box.left) * scale), 0, width),
      y: clamp(Math.round((event.clientY - box.top) * scale), 0, height),
    };
  };

  const drag = (event: PointerEvent<HTMLElement>) => {
    if (!start) {
      return;
    }
    const point = toPixels(event);
    onSelect({
      x: Math.min(start.x, point.x),
      y: Math.min(start.y, point.y),
      width: Math.abs(point.x - start.x),
      height: Math.abs(point.y - start.y),
    });
  };

  const setField = (field: keyof Area, value: string) => {
    const current = area ?? { x: 0, y: 0, width: 0, height: 0 };
    onSelect({ ...current, [field]: Math.max(0, Number.parseInt(value, 10) || 0) });
  };

  return (
    <div className="crop">
      <div
        className="crop-canvas"
        onPointerDown={(event) => {
          event.currentTarget.setPointerCapture(event.pointerId);
          const point = toPixels(event);
          setStart(point);
          onSelect({ ...point, width: 0, height: 0 });
        }}
        onPointerMove={drag}
        onPointerUp={() => setStart(null)}
      >
        <img ref={image} src={src} alt="The current screen" draggable={false} />
        {area && area.width > 0 && area.height > 0 && (
          <div
            className="crop-area"
            style={{
              left: `${(area.x / width) * 100}%`,
              top: `${(area.y / height) * 100}%`,
              width: `${(area.width / width) * 100}%`,
              height: `${(area.height / height) * 100}%`,
            }}
          />
        )}
      </div>
      <fieldset className="crop-fields">
        <legend>Selected area in pixels of the {width} × {height} capture</legend>
        {(['x', 'y', 'width', 'height'] as const).map((field) => (
          <label key={field}>
            {field}
            <input type="number" min={0} value={area?.[field] ?? 0} onChange={(event) => setField(field, event.target.value)} />
          </label>
        ))}
      </fieldset>
    </div>
  );
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
