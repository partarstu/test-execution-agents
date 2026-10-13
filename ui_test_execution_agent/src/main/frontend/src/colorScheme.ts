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
import { useSyncExternalStore } from 'react';

export type ThemeChoice = 'system' | 'light' | 'dark';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'panel.theme';

const listeners = new Set<() => void>();
let choice: ThemeChoice = 'system';
let darkQuery: MediaQueryList | null = null;

/** Applies the stored choice and follows the system scheme while the choice is *system*; called once before the first render. */
export function initColorScheme(): void {
  choice = storedChoice();
  darkQuery = matchMedia('(prefers-color-scheme: dark)');
  darkQuery.addEventListener('change', () => {
    if (choice === 'system') {
      apply();
    }
  });
  apply();
}

export function setThemeChoice(next: ThemeChoice): void {
  choice = next;
  try {
    if (next === 'system') {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, next);
    }
  } catch {
    // Blocked storage only means the choice lasts until the page is reloaded
  }
  apply();
}

export function useThemeChoice(): ThemeChoice {
  return useSyncExternalStore(subscribe, () => choice);
}

/** The theme in effect, for the canvas views that read the colour tokens and must redraw when it changes. */
export function useTheme(): Theme {
  return useSyncExternalStore(subscribe, resolvedTheme);
}

function resolvedTheme(): Theme {
  if (choice !== 'system') {
    return choice;
  }
  return darkQuery?.matches ? 'dark' : 'light';
}

function apply() {
  document.documentElement.dataset.theme = resolvedTheme();
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function storedChoice(): ThemeChoice {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'system';
  } catch {
    return 'system';
  }
}
