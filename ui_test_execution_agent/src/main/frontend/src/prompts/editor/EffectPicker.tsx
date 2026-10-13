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
import { useEffect, useEffectEvent, useId, useState } from 'react';
import type { ChildRef } from '../Prompt';
import type { EditorEnv } from './ProcedureForm';

interface Props {
  levels: ChildRef[][];
  env: EditorEnv;
  onPick: (effect: string) => void;
  onBack: () => void;
}

/** Picks a prerequisite from the effects that hold before the procedure runs, newest first, so it matches them word for word. */
export function EffectPicker({ levels, env, onPick, onBack }: Props) {
  const [effects, setEffects] = useState<string[] | null>(null);
  const [filter, setFilter] = useState('');
  const filterId = useId();
  const load = useEffectEvent(() => env.call('effects-before-child', { levels }));

  useEffect(() => {
    let current = true;
    void load().then((response) => {
      if (current) {
        setEffects(response?.effects ?? []);
      }
    });
    return () => {
      current = false;
    };
  }, []);

  const shown = (effects ?? []).filter((effect) => effect.toLowerCase().includes(filter.trim().toLowerCase()));

  return (
    <section className="subview" aria-label="Pick a prerequisite">
      <button type="button" className="back" onClick={onBack}><ArrowLeft size={16} />Back to the procedure</button>
      <h3>Pick a prerequisite</h3>
      <p className="prose">These states hold before this procedure runs, the most recent first.</p>
      <label htmlFor={filterId}>Filter</label>
      <input id={filterId} type="search" value={filter} onChange={(event) => setFilter(event.target.value)} autoFocus />
      {effects === null ? (
        <p className="list-state">Loading the effects…</p>
      ) : shown.length === 0 ? (
        <p className="empty">{effects.length === 0 ? 'No earlier step produces an effect.' : 'No effect matches the filter.'}</p>
      ) : (
        <ul className="choice-list">
          {shown.map((effect) => (
            <li key={effect} className="choice">
              <span className="choice-title">{effect}</span>
              <button type="button" onClick={() => onPick(effect)}>Use</button>
            </li>
          ))}
        </ul>
      )}
      <button type="button" onClick={() => onPick('')}>Type a prerequisite instead</button>
    </section>
  );
}
