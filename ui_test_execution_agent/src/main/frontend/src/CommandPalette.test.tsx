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
import { Activity, HeartPulse, Sun } from 'lucide-react';
import { describe, expect, it, vi } from 'vitest';
import { CommandPalette, isOutside } from './CommandPalette';
import { matchCommands, type Command } from './commands';
import { axeViolations, installDom } from './testing/dom';

installDom();

function commands(run = vi.fn()): Command[] {
  return [
    { id: 'run', label: 'Run', group: 'Go to', Icon: Activity, run: () => run('run') },
    { id: 'health', label: 'Graph health', group: 'Go to', Icon: HeartPulse, run: () => run('health') },
    { id: 'light', label: 'Use the light theme', group: 'Theme', Icon: Sun, run: () => run('light') },
  ];
}

describe('matchCommands', () => {
  it('keeps the commands whose label or group holds every word, label starts first', () => {
    expect(matchCommands(commands(), 'go h').map(({ id }) => id)).toEqual(['health']);
    expect(matchCommands(commands(), 'theme').map(({ id }) => id)).toEqual(['light']);
    expect(matchCommands(commands(), 'g').map(({ id }) => id)).toEqual(['health', 'run', 'light']);
  });

  it('keeps every command for an empty query', () => {
    expect(matchCommands(commands(), '  ')).toHaveLength(3);
  });
});

describe('CommandPalette', () => {
  function renderPalette(run = vi.fn(), onClose = vi.fn()) {
    render(<CommandPalette open commands={commands(run)} onClose={onClose} />);
    return { run, field: screen.getByRole('combobox', { name: 'Search and jump' }) };
  }

  it('filters as the operator types and runs the active command on Enter', () => {
    const { run, field } = renderPalette();

    fireEvent.change(field, { target: { value: 'health' } });
    expect(screen.getAllByRole('option')).toHaveLength(1);
    fireEvent.keyDown(field, { key: 'Enter' });

    expect(run).toHaveBeenCalledWith('health');
  });

  it('moves the active option with the arrows, wrapping around, and keeps the focus in the field', () => {
    const { run, field } = renderPalette();

    fireEvent.keyDown(field, { key: 'ArrowUp' });

    const active = screen.getByRole('option', { selected: true });
    expect(active.textContent).toContain('Use the light theme');
    expect(field.getAttribute('aria-activedescendant')).toBe(active.id);
    expect(document.activeElement).toBe(field);
    expect(run).not.toHaveBeenCalled();
  });

  it('runs a command picked with the pointer', () => {
    const { run } = renderPalette();

    fireEvent.mouseDown(screen.getByRole('option', { name: /Graph health/ }));

    expect(run).toHaveBeenCalledWith('health');
  });

  it('says when nothing matches', async () => {
    const { field } = renderPalette();

    fireEvent.change(field, { target: { value: 'nothing at all' } });

    expect(screen.getByRole('status').textContent).toBe('No command matches “nothing at all”.');
    expect(await axeViolations()).toEqual([]);
  });

  it('has no axe violations', async () => {
    renderPalette();

    expect(await axeViolations()).toEqual([]);
  });

  it('closes on a click on the backdrop, without relying on closedby', () => {
    renderPalette();
    const dialog = screen.getByRole('dialog');
    dialog.getBoundingClientRect = () => DOMRect.fromRect({ x: 100, y: 100, width: 400, height: 300 });

    fireEvent.click(dialog, { clientX: 300, clientY: 200 });
    expect((dialog as HTMLDialogElement).open).toBe(true);

    fireEvent.click(dialog, { clientX: 20, clientY: 20 });
    expect((dialog as HTMLDialogElement).open).toBe(false);
  });
});

describe('isOutside', () => {
  const box = { left: 10, right: 110, top: 20, bottom: 70 };

  it.each([
    [5, 40, true],
    [120, 40, true],
    [50, 10, true],
    [50, 80, true],
    [50, 40, false],
    [10, 20, false],
  ])('tells a click at (%i, %i) outside the box: %s', (clientX, clientY, outside) => {
    expect(isOutside(box, { clientX, clientY })).toBe(outside);
  });
});
