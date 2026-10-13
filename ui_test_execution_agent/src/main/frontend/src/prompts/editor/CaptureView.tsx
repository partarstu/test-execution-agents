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
import { ArrowLeft } from 'lucide-react';
import { useState } from 'react';
import { ScreenshotCrop, type Area } from '../../kit/ScreenshotCrop';
import { imageUrl } from '../api';
import type { CapturedScreen, ElementCard } from '../Prompt';
import type { EditorEnv } from './ProcedureForm';

interface Props {
  element: ElementCard;
  env: EditorEnv;
  onSaved: (element: ElementCard) => void;
  onBack: () => void;
}

/** Replaces an element's screenshot with an area of a fresh capture of the agent's screen. */
export function CaptureView({ element, env, onSaved, onBack }: Props) {
  const [capture, setCapture] = useState<CapturedScreen | null>(null);
  const [area, setArea] = useState<Area | null>(null);

  const captureScreen = async () => {
    const captured = await env.call('capture-screen', {});
    if (captured) {
      setCapture(captured);
      setArea(null);
    }
  };

  const save = async () => {
    if (!capture || !area) {
      return;
    }
    const saved = await env.call('replace-element-screenshot', { elementId: element.id, screenshotId: capture.screenshotId, ...area });
    if (saved) {
      onSaved(saved);
    }
  };

  return (
    <section className="subview" aria-label="Replace the element screenshot">
      <button type="button" className="back" onClick={onBack}><ArrowLeft size={16} />Back without replacing</button>
      <h3>New screenshot of “{element.name}”</h3>
      <p className="prose">
        Bring the element into view on the agent's screen, for example through the live view, then capture the screen and drag a
        rectangle around the element.
      </p>
      <div className="answers">
        <button type="button" className={capture ? undefined : 'primary'} disabled={env.pending} onClick={() => void captureScreen()}>
          {capture ? 'Capture again' : 'Capture the screen'}
        </button>
      </div>
      {capture && (
        <>
          <ScreenshotCrop src={imageUrl(env.promptId, capture.screenshotId)} width={capture.width} height={capture.height} area={area}
            onSelect={setArea} />
          <div className="answers">
            <button type="button" className="primary" disabled={env.pending || !area || area.width === 0 || area.height === 0}
              onClick={() => void save()}>
              Use the selected area
            </button>
          </div>
        </>
      )}
    </section>
  );
}
