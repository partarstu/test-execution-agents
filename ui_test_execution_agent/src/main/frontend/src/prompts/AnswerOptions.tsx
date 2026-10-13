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
import type { LucideIcon } from 'lucide-react';
import { useId } from 'react';
import { useAnswerKeys } from './useAnswerKeys';

export interface AnswerOption {
  id: string;
  label: string;
  hint: string;
  Icon: LucideIcon;
  tone?: 'primary' | 'danger';
  onChoose: () => void;
}

/**
 * A prompt's answers as cards named by their label and described by what they do, numbered for the keys 1 to 9 unless `numbered`
 * is false; the first takes the focus, which a modal prompt only honours through `data-autofocus`.
 */
export function AnswerOptions({ options, disabled, autoFocus = true, numbered = true }: {
  options: AnswerOption[];
  disabled: boolean;
  autoFocus?: boolean;
  numbered?: boolean;
}) {
  const idPrefix = useId();
  useAnswerKeys(options.map((option) => option.onChoose), numbered && !disabled);
  return (
    <div className="options">
      {options.map(({ id, label, hint, Icon, tone, onChoose }, index) => (
        <button
          key={id}
          type="button"
          className={tone ? `option option-${tone}` : 'option'}
          autoFocus={autoFocus && index === 0}
          data-autofocus={autoFocus && index === 0 ? true : undefined}
          disabled={disabled}
          aria-keyshortcuts={numbered && index < 9 ? String(index + 1) : undefined}
          aria-labelledby={`${idPrefix}-${id}-label`}
          aria-describedby={`${idPrefix}-${id}-hint`}
          onClick={onChoose}
        >
          <span className="option-icon"><Icon size={20} /></span>
          <span className="option-text">
            <span id={`${idPrefix}-${id}-label`} className="option-label">{label}</span>
            <span id={`${idPrefix}-${id}-hint`} className="option-hint">{hint}</span>
          </span>
          {numbered && index < 9 && <kbd className="kbd option-key" aria-hidden="true">{index + 1}</kbd>}
        </button>
      ))}
    </div>
  );
}
