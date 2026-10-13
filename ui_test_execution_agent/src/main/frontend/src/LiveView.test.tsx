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
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Activity } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LiveView } from './LiveView';
import { axeViolations, installDom } from './testing/dom';

const { clients } = vi.hoisted(() => ({ clients: [] as { viewOnly: boolean; disconnected: boolean; dispatchEvent: (event: Event) => boolean }[] }));

vi.mock('@novnc/novnc', () => ({
  default: class extends EventTarget {
    viewOnly = true;
    scaleViewport = false;
    resizeSession = false;
    disconnected = false;

    constructor() {
      super();
      clients.push(this);
    }

    disconnect() {
      this.disconnected = true;
    }
  },
}));

installDom();

beforeEach(() => {
  clients.length = 0;
});

async function renderLive(liveControl = true, presentation: 'tile' | 'large' = 'large', onToggleSize = vi.fn()) {
  const view = render(<LiveView liveControl={liveControl} presentation={presentation} onToggleSize={onToggleSize} />);
  await waitFor(() => expect(clients).toHaveLength(1));
  return view;
}

describe('LiveView', () => {
  it('hands the keyboard back on Shift+Escape before the screen sees it', async () => {
    const { container } = await renderLive();
    const toggle = screen.getByRole('button', { name: 'Control the screen' });
    fireEvent.click(toggle);
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    expect(clients[0].viewOnly).toBe(false);

    const key = new KeyboardEvent('keydown', { key: 'Escape', shiftKey: true, bubbles: true, cancelable: true });
    const screenListener = vi.fn();
    const canvas = container.querySelector('.live-screen')!;
    canvas.addEventListener('keydown', screenListener);
    act(() => {
      canvas.dispatchEvent(key);
    });

    expect(toggle.getAttribute('aria-pressed')).toBe('false');
    expect(clients[0].viewOnly).toBe(true);
    expect(document.activeElement).toBe(toggle);
    expect(screenListener).not.toHaveBeenCalled();
  });

  it('offers to reconnect once the screen is lost, with a fresh client', async () => {
    await renderLive();

    act(() => {
      clients[0].dispatchEvent(new Event('disconnect'));
    });
    fireEvent.click(screen.getByRole('button', { name: 'Reconnect' }));

    await waitFor(() => expect(clients).toHaveLength(2));
    expect(clients[0].disconnected).toBe(true);
    expect(screen.getByRole('status').textContent).toBe('Connecting…');
  });

  it('ignores the late disconnect of a replaced client', async () => {
    await renderLive();
    act(() => {
      clients[0].dispatchEvent(new Event('disconnect'));
    });
    fireEvent.click(screen.getByRole('button', { name: 'Reconnect' }));
    await waitFor(() => expect(clients).toHaveLength(2));

    act(() => {
      clients[1].dispatchEvent(new Event('connect'));
      clients[0].dispatchEvent(new Event('disconnect'));
    });

    expect(screen.getByRole('status').textContent).toBe('Showing the agent’s screen');
    expect(screen.queryByRole('button', { name: 'Reconnect' })).toBeNull();
  });

  it('shows a fresh client as connecting when the hidden tab is shown again', async () => {
    const view = render(<Activity mode="visible"><LiveView liveControl presentation="large" onToggleSize={vi.fn()} /></Activity>);
    await waitFor(() => expect(clients).toHaveLength(1));
    act(() => {
      clients[0].dispatchEvent(new Event('connect'));
    });

    view.rerender(<Activity mode="hidden"><LiveView liveControl presentation="large" onToggleSize={vi.fn()} /></Activity>);
    view.rerender(<Activity mode="visible"><LiveView liveControl presentation="large" onToggleSize={vi.fn()} /></Activity>);

    await waitFor(() => expect(clients).toHaveLength(2));
    expect(clients[0].disconnected).toBe(true);
    expect(screen.getByRole('status').textContent).toBe('Connecting…');
  });

  it('is a view-only tile that opens large, by its button or a click anywhere on the screen', async () => {
    const onToggleSize = vi.fn();
    const { container } = await renderLive(true, 'tile', onToggleSize);

    expect(screen.queryByRole('button', { name: 'Control the screen' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Show full screen' })).toBeNull();
    expect(clients[0].viewOnly).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: /Open large/ }));
    fireEvent.click(container.querySelector('.live-tile-open')!);

    expect(onToggleSize).toHaveBeenCalledTimes(2);
    expect(await axeViolations()).toEqual([]);
  });

  it('gives the screen back to view only when it turns into a tile while controlled', async () => {
    const view = await renderLive();
    fireEvent.click(screen.getByRole('button', { name: 'Control the screen' }));
    expect(clients[0].viewOnly).toBe(false);

    view.rerender(<LiveView liveControl presentation="tile" onToggleSize={vi.fn()} />);

    expect(clients[0].viewOnly).toBe(true);
    expect(clients).toHaveLength(1);
    expect(screen.getByRole('button', { name: /Open large/ })).toBeTruthy();
  });

  it('goes back to the step from the large view', async () => {
    const onToggleSize = vi.fn();
    await renderLive(true, 'large', onToggleSize);

    fireEvent.click(screen.getByRole('button', { name: /Back to the step/ }));

    expect(onToggleSize).toHaveBeenCalledOnce();
  });

  it('only watches where the deployment allows no control', async () => {
    await renderLive(false);

    expect(screen.queryByRole('button', { name: 'Control the screen' })).toBeNull();
    expect(screen.getByText('Watching only')).toBeTruthy();
    expect(await axeViolations()).toEqual([]);
  });

  it('explains on demand why the view only watches', async () => {
    await renderLive(false);
    const toggle = screen.getByRole('button', { name: 'Watching only' });

    fireEvent.click(toggle);

    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(document.getElementById(toggle.getAttribute('aria-controls') ?? '')?.textContent)
      .toContain('your mouse and keyboard do not reach the agent’s screen');
    expect(await axeViolations()).toEqual([]);

    fireEvent.click(toggle);

    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByText(/do not reach the agent’s screen/)).toBeNull();
  });
});
