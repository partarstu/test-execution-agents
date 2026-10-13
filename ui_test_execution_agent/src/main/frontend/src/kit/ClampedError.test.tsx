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
// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { axeViolations, installDom } from '../testing/dom';
import { ClampedError } from './ClampedError';

installDom();

const ERROR = 'Element not found\nat Locator.find\nat Step.run\nat Agent.execute\nat Runner.main';

// jsdom lays nothing out, so the heights that tell a clamped text apart are set by hand
function layOut(scrollHeight: number, clientHeight: number) {
  Object.defineProperty(HTMLElement.prototype, 'scrollHeight', { configurable: true, get: () => scrollHeight });
  Object.defineProperty(HTMLElement.prototype, 'clientHeight', { configurable: true, get: () => clientHeight });
}

describe('ClampedError', () => {
  afterEach(() => {
    Reflect.deleteProperty(HTMLElement.prototype, 'scrollHeight');
    Reflect.deleteProperty(HTMLElement.prototype, 'clientHeight');
  });

  it('shows a short error without a button', () => {
    layOut(40, 40);
    render(<ClampedError text="Element not found" />);

    expect(screen.getByText('Element not found')).toBeTruthy();
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('cuts a long error to its first lines and shows the whole text on request', async () => {
    layOut(120, 80);
    render(<ClampedError text={ERROR} />);
    const text = screen.getByText(/Element not found/);
    const toggle = screen.getByRole('button', { name: 'Show the whole error' });
    expect(text.classList.contains('clamped-error-collapsed')).toBe(true);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-controls')).toBe(text.id);

    fireEvent.click(toggle);

    expect(text.classList.contains('clamped-error-collapsed')).toBe(false);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(toggle.textContent).toBe('Show less');
    expect(await axeViolations()).toEqual([]);

    fireEvent.click(toggle);

    expect(text.classList.contains('clamped-error-collapsed')).toBe(true);
  });
});
