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
import { act, fireEvent, render, screen } from '@testing-library/react';
import { Search } from 'lucide-react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { axeViolations, installDom } from '../testing/dom';
import { IconButton } from './IconButton';
import { Screenshot } from './Screenshot';

installDom();

// jsdom hides a popover even once the stub has shown it, so the tooltip is found by its role attribute
const tooltip = () => document.querySelector<HTMLElement>('[role="tooltip"]')!;

const shown = () => tooltip().hasAttribute('data-popover-open');

const waitPastDelay = () => act(() => vi.advanceTimersByTime(500));

describe('tooltips', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows the label of an icon button on keyboard focus and keeps it as the accessible name only', async () => {
    render(<IconButton icon={Search} label="Search" />);
    const button = screen.getByRole('button', { name: 'Search' });

    fireEvent.focus(button);
    await waitPastDelay();

    expect(shown()).toBe(true);
    expect(tooltip().textContent).toBe('Search');
    expect(button.getAttribute('aria-describedby')).toBeNull();
    expect(button.hasAttribute('title')).toBe(false);
    vi.useRealTimers();
    expect(await axeViolations()).toEqual([]);
  });

  it('hides on Escape without the key reaching the page', async () => {
    const onPageKey = vi.fn();
    document.addEventListener('keydown', onPageKey);
    render(<IconButton icon={Search} label="Search" />);
    const button = screen.getByRole('button', { name: 'Search' });
    fireEvent.focus(button);
    await waitPastDelay();

    fireEvent.keyDown(button, { key: 'Escape' });

    expect(shown()).toBe(false);
    expect(onPageKey).not.toHaveBeenCalled();
    document.removeEventListener('keydown', onPageKey);
  });

  it('shows on hover, stays while the pointer moves onto it and hides once the pointer leaves both', async () => {
    render(<IconButton icon={Search} label="Search" />);
    const button = screen.getByRole('button', { name: 'Search' });

    fireEvent.pointerEnter(button);
    await waitPastDelay();
    expect(shown()).toBe(true);

    fireEvent.pointerLeave(button);
    fireEvent.pointerEnter(tooltip());
    await waitPastDelay();
    expect(shown()).toBe(true);

    fireEvent.pointerLeave(tooltip());
    await waitPastDelay();
    expect(shown()).toBe(false);
  });

  it('stays hidden when a click focuses the button', async () => {
    render(<IconButton icon={Search} label="Search" />);
    const button = screen.getByRole('button', { name: 'Search' });

    fireEvent.pointerDown(button);
    fireEvent.focus(button);
    await waitPastDelay();

    expect(shown()).toBe(false);
  });

  it('describes what a screenshot button does instead of the native title', () => {
    render(<Screenshot src="/shot.png" alt="The Save dialog" />);
    const button = screen.getByRole('button', { name: 'The Save dialog' });

    expect(button.hasAttribute('title')).toBe(false);
    expect(document.getElementById(button.getAttribute('aria-describedby') ?? '')?.textContent).toBe('Show at full size');
  });
});
