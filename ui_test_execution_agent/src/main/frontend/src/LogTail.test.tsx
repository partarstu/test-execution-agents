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
import { afterEach, describe, expect, it, vi } from 'vitest';
import { COPY_BLOCKED } from './clipboard';
import { lineParts, levelOf, LogTail } from './LogTail';
import { installDom } from './testing/dom';

installDom();

describe('levelOf', () => {
  it.each([
    ['2026-10-04 10:17:41 ERROR o.t.Verifier - failed', 'error'],
    ['2026-10-04 10:17:41 SEVERE something broke', 'error'],
    ['2026-10-04 10:17:27 WARN  o.t.Locator - retrying', 'warn'],
    ['2026-10-04 10:17:27 WARNING slow page', 'warn'],
    ['2026-10-04 10:17:34 INFO  o.t.Agent - Click at (1312, 644)', 'other'],
    ['The agent found no ERRORS', 'other'],
  ])('reads the level of "%s" as %s', (line, level) => {
    expect(levelOf(line)).toBe(level);
  });
});

describe('LogTail', () => {
  it('is a focusable region that never announces lines as they stream in', () => {
    render(<LogTail lines={['first', 'second']} firstLine={0} onClose={vi.fn()} />);

    const region = screen.getByRole('region', { name: 'Agent logs' });
    expect(region.getAttribute('tabindex')).toBe('0');
    expect(region.getAttribute('aria-live')).toBeNull();
    expect(region.textContent).toBe('secondfirst');
  });

  it('shows the newest line on top and keeps it in view while following', () => {
    const { rerender } = render(<LogTail lines={['first', 'second']} firstLine={0} onClose={vi.fn()} />);
    const region = screen.getByRole('region', { name: 'Agent logs' });
    region.scrollTop = 10;

    rerender(<LogTail lines={['first', 'second', 'third']} firstLine={0} onClose={vi.fn()} />);

    expect(region.textContent).toBe('thirdsecondfirst');
    expect(region.scrollTop).toBe(0);
  });

  it('keeps the place of an operator who scrolled down to read and offers to jump to the newest', () => {
    const { rerender } = render(<LogTail lines={['first', 'second']} firstLine={0} onClose={vi.fn()} />);
    const region = screen.getByRole('region', { name: 'Agent logs' });
    region.scrollTop = 100;
    fireEvent.scroll(region);

    rerender(<LogTail lines={['first', 'second', 'third']} firstLine={0} onClose={vi.fn()} />);
    expect(region.scrollTop).toBe(100);

    fireEvent.click(screen.getByRole('button', { name: 'Jump to the newest' }));
    expect(region.scrollTop).toBe(0);
  });

  it('keeps a line in its element while older lines drop off the front', () => {
    const { rerender } = render(<LogTail lines={['first', 'second']} firstLine={0} onClose={vi.fn()} />);
    const second = screen.getByText('second');

    rerender(<LogTail lines={['second', 'third']} firstLine={1} onClose={vi.fn()} />);

    expect(screen.getByText('second')).toBe(second);
  });
});

describe('lineParts', () => {
  it('splits a line of the agent into time, level, logger and message', () => {
    expect(lineParts('10:17:41.042 WARN  Locator - retrying - twice')).toEqual({
      time: '10:17:41.042', level: 'WARN', logger: 'Locator', message: 'retrying - twice',
    });
  });

  it('leaves a line in another shape alone', () => {
    expect(lineParts('first')).toBeNull();
  });
});

describe('LogTail filters', () => {
  const lines = ['10:00:00.000 INFO  Agent - Clicked Save', '10:00:01.000 WARN  Locator - retrying', '10:00:02.000 ERROR Verifier - Save failed'];

  it('shows only the lines of the chosen level, with the counts per level', () => {
    render(<LogTail lines={lines} firstLine={0} onClose={vi.fn()} />);

    fireEvent.click(screen.getByRole('radio', { name: /Errors/ }));

    expect(screen.getByRole('region', { name: 'Agent logs' }).textContent).toBe('10:00:02.000 ERROR Verifier - Save failed');
    expect(screen.getByText('1 of 3 lines')).toBeTruthy();
    expect(screen.getByRole('radio', { name: 'Warnings 1' })).toBeTruthy();
  });

  it('shows only the lines with the text and marks it', () => {
    render(<LogTail lines={lines} firstLine={0} onClose={vi.fn()} />);

    fireEvent.change(screen.getByRole('searchbox', { name: 'Find in the log' }), { target: { value: 'save' } });

    expect([...document.querySelectorAll('mark')].map((mark) => mark.textContent)).toEqual(['Save', 'Save']);
    expect(screen.getByText('2 of 3 lines')).toBeTruthy();
  });

  it('says so when no line matches', () => {
    render(<LogTail lines={lines} firstLine={0} onClose={vi.fn()} />);

    fireEvent.change(screen.getByRole('searchbox', { name: 'Find in the log' }), { target: { value: 'nothing' } });

    expect(screen.getByText('No line matches.')).toBeTruthy();
  });
});

describe('LogTail copy', () => {
  const lines = ['10:00:00.000 INFO  Agent - Clicked Save', '10:00:02.000 ERROR Verifier - Save failed'];

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('copies the lines shown and says so', async () => {
    const writeText = vi.fn(async () => undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    render(<LogTail lines={lines} firstLine={0} onClose={vi.fn()} />);

    fireEvent.click(screen.getByRole('radio', { name: /Errors/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Copy the lines shown' }));

    expect((await screen.findByRole('status')).textContent).toBe('Copied');
    expect(writeText).toHaveBeenCalledWith('10:00:02.000 ERROR Verifier - Save failed');
  });

  it('copies the lines oldest first, as a log is read elsewhere', async () => {
    const writeText = vi.fn(async () => undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    render(<LogTail lines={lines} firstLine={0} onClose={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'Copy the log' }));

    await screen.findByText('Copied');
    expect(writeText).toHaveBeenCalledWith(lines.join('\n'));
  });

  it('says that the browser blocked copying when the Clipboard API is missing', async () => {
    vi.stubGlobal('navigator', {});
    render(<LogTail lines={lines} firstLine={0} onClose={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'Copy the log' }));

    expect((await screen.findByText(COPY_BLOCKED)).getAttribute('role')).toBe('status');
  });
});
