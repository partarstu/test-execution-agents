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
import { Plus, X } from 'lucide-react';
import { useId, type ReactNode } from 'react';
import { IconButton } from './IconButton';
import './TextListField.css';

interface Props {
  label: string;
  hint?: string;
  items: string[];
  onChange: (items: string[]) => void;
  /** An extra control next to Add, e.g. picking from what earlier steps produce. */
  extra?: ReactNode;
}

/** A list of short texts edited in place: each entry is its own field, so typing never needs a separate dialog. */
export function TextListField({ label, hint, items, onChange, extra }: Props) {
  const labelId = useId();
  const replace = (index: number, value: string) => onChange(items.map((item, at) => (at === index ? value : item)));
  return (
    <fieldset className="list-field" aria-labelledby={labelId}>
      <legend id={labelId}>{label}</legend>
      {hint && <p className="field-hint">{hint}</p>}
      {items.length === 0 && <p className="empty">None yet.</p>}
      <ul>
        {items.map((item, index) => (
          <li key={index}>
            <input
              type="text"
              value={item}
              aria-label={`${label} ${index + 1}`}
              onChange={(event) => replace(index, event.target.value)}
              autoFocus={item === '' && index === items.length - 1}
            />
            <IconButton icon={X} label={`Remove ${label.toLowerCase()} ${index + 1}`} onClick={() => onChange(items.filter((_, at) => at !== index))} />
          </li>
        ))}
      </ul>
      <div className="list-field-actions">
        <button type="button" className="small" onClick={() => onChange([...items, ''])}><Plus size={16} />Add</button>
        {extra}
      </div>
    </fieldset>
  );
}

/** The entries with text, as saved. */
export function filled(items: string[]): string[] {
  return items.map((item) => item.trim()).filter((item) => item !== '');
}
