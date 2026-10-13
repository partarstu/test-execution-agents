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
import { describe, expect, it, vi } from 'vitest';
import { useShortcuts } from './shortcuts';
import { installDom } from './testing/dom';

installDom();

function renderShortcuts(goToPrompt: (() => void) | null = vi.fn()) {
  const handlers = { openPalette: vi.fn(), showShortcuts: vi.fn(), select: vi.fn(), goToPrompt, toggleLogs: vi.fn(), toggleLiveView: vi.fn() };
  function Harness() {
    useShortcuts(handlers);
    return <input aria-label="A field" />;
  }
  render(<Harness />);
  return handlers;
}

describe('useShortcuts', () => {
  it('opens the palette with Ctrl+K or Cmd+K, even from a field', () => {
    const handlers = renderShortcuts();

    fireEvent.keyDown(screen.getByRole('textbox'), { key: 'k', ctrlKey: true });
    fireEvent.keyDown(document.body, { key: 'K', metaKey: true });

    expect(handlers.openPalette).toHaveBeenCalledTimes(2);
  });

  it('goes to a view with G then its letter', () => {
    const handlers = renderShortcuts();

    fireEvent.keyDown(document.body, { key: 'g' });
    fireEvent.keyDown(document.body, { key: 'h' });

    expect(handlers.select).toHaveBeenCalledWith('health');
  });

  it('takes W to the waiting question only while there is one', () => {
    const goToPrompt = vi.fn();
    renderShortcuts(goToPrompt);
    fireEvent.keyDown(document.body, { key: 'w' });
    expect(goToPrompt).toHaveBeenCalledTimes(1);
  });

  it('toggles the logs with L and the large live view with V', () => {
    const handlers = renderShortcuts();

    fireEvent.keyDown(document.body, { key: 'l' });
    fireEvent.keyDown(document.body, { key: 'V' });

    expect(handlers.toggleLogs).toHaveBeenCalledOnce();
    expect(handlers.toggleLiveView).toHaveBeenCalledOnce();
  });

  it('shows the shortcuts with ?', () => {
    const handlers = renderShortcuts();

    fireEvent.keyDown(document.body, { key: '?' });

    expect(handlers.showShortcuts).toHaveBeenCalled();
  });

  it('leaves the keys typed into a field alone', () => {
    const handlers = renderShortcuts();
    const field = screen.getByRole('textbox');

    fireEvent.keyDown(field, { key: 'g' });
    fireEvent.keyDown(field, { key: 'h' });
    fireEvent.keyDown(field, { key: '?' });

    expect(handlers.select).not.toHaveBeenCalled();
    expect(handlers.showShortcuts).not.toHaveBeenCalled();
  });

  it('stays out of an open dialog', () => {
    const handlers = renderShortcuts();
    const dialog = document.createElement('dialog');
    dialog.open = true;
    document.body.append(dialog);

    fireEvent.keyDown(document.body, { key: 'k', ctrlKey: true });
    fireEvent.keyDown(document.body, { key: '?' });
    dialog.remove();

    expect(handlers.openPalette).not.toHaveBeenCalled();
    expect(handlers.showShortcuts).not.toHaveBeenCalled();
  });
});
