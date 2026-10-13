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
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { LoginView } from './LoginView';
import { axeViolations, installDom } from './testing/dom';
import type { Displaced } from './types';

installDom();

const TAKEN_OVER: Displaced = { reason: 'taken-over', at: '2026-10-04T10:00:00Z', sourceIp: '10.0.0.7', browser: 'Firefox' };

describe('LoginView', () => {
  it('is one main landmark with the panel heading, a labelled password field and no axe violations', async () => {
    render(<LoginView notice={null} onLoggedIn={vi.fn()} />);

    expect(screen.getByRole('main')).toBeTruthy();
    expect(screen.getByRole('heading', { level: 1, name: 'Operator panel' })).toBeTruthy();
    expect(document.activeElement).toBe(screen.getByLabelText('Panel password'));
    expect(await axeViolations()).toEqual([]);
  });

  it('has no axe violations with the notice of a taken-over session', async () => {
    render(<LoginView notice={TAKEN_OVER} onLoggedIn={vi.fn()} />);

    expect(await axeViolations()).toEqual([]);
  });
});
