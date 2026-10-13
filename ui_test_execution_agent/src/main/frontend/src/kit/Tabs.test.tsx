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
import { axeViolations, installDom } from '../testing/dom';
import { TabList, tabPanelProps } from './Tabs';

installDom();

const TABS = [{ id: 'run', label: 'Run' }, { id: 'health', label: 'Health' }, { id: 'knowledge', label: 'Knowledge' }];

function renderTabs(onSelect = vi.fn()) {
  render(
    <>
      <TabList label="Panel views" idPrefix="view" tabs={TABS} selected="run" onSelect={onSelect} />
      <div {...tabPanelProps('view', 'run')}>Run content</div>
    </>,
  );
  return onSelect;
}

describe('TabList', () => {
  it('moves the focus with the arrows, Home and End without selecting', () => {
    const onSelect = renderTabs();
    const [run, health, knowledge] = screen.getAllByRole('tab');
    run.focus();

    fireEvent.keyDown(run, { key: 'ArrowRight' });
    expect(document.activeElement).toBe(health);
    fireEvent.keyDown(health, { key: 'End' });
    expect(document.activeElement).toBe(knowledge);
    fireEvent.keyDown(knowledge, { key: 'ArrowRight' });
    expect(document.activeElement).toBe(run);
    fireEvent.keyDown(run, { key: 'ArrowLeft' });
    expect(document.activeElement).toBe(knowledge);
    fireEvent.keyDown(knowledge, { key: 'Home' });
    expect(document.activeElement).toBe(run);
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('selects a tab when it is activated', () => {
    const onSelect = renderTabs();

    fireEvent.click(screen.getByRole('tab', { name: 'Health' }));

    expect(onSelect).toHaveBeenCalledWith('health');
  });

  it('keeps only the selected tab in the tab order and ties it to its panel', async () => {
    renderTabs();

    expect(screen.getAllByRole('tab').map((tab) => tab.tabIndex)).toEqual([0, -1, -1]);
    expect(screen.getByRole('tabpanel', { name: 'Run' }).textContent).toBe('Run content');
    expect(await axeViolations()).toEqual([]);
  });
});
