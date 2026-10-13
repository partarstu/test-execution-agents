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
import {
  Activity, Copy, HeartPulse, Keyboard, LogOut, MessageCircleQuestion, Monitor, Moon, ScrollText, Sun, Waypoints, type LucideIcon,
} from 'lucide-react';
import { setThemeChoice } from './colorScheme';
import { KNOWLEDGE_VIEWS, type KnowledgeView } from './knowledge/views';
import type { Tab } from './tabs';

export interface Command {
  id: string;
  label: string;
  group: string;
  Icon: LucideIcon;
  run: () => void;
}

interface Actions {
  select: (tab: Tab) => void;
  showKnowledgeView: (view: KnowledgeView) => void;
  goToPrompt: (() => void) | null;
  showShortcuts: () => void;
  copyLog: (() => void) | null;
  logsOpen: boolean;
  toggleLogs: () => void;
  liveViewLarge: boolean;
  /** Null without a live view. */
  toggleLiveView: (() => void) | null;
  signOut: () => void;
}

/** Everything the command palette offers, the waiting question first while there is one. */
export function panelCommands({
  select, showKnowledgeView, goToPrompt, showShortcuts, copyLog, logsOpen, toggleLogs, liveViewLarge, toggleLiveView, signOut,
}: Actions): Command[] {
  const commands: Command[] = [];
  if (goToPrompt) {
    commands.push({ id: 'prompt', label: 'Answer the waiting question', group: 'Run', Icon: MessageCircleQuestion, run: goToPrompt });
  }
  commands.push({ id: 'logs', label: logsOpen ? 'Hide the logs' : 'Show the logs', group: 'Run', Icon: ScrollText, run: toggleLogs });
  if (toggleLiveView) {
    commands.push({
      id: 'live-view', label: liveViewLarge ? 'Back to the step' : 'Open the live view large', group: 'Run', Icon: Monitor,
      run: toggleLiveView,
    });
  }
  commands.push(
    { id: 'tab-run', label: 'Run', group: 'Go to', Icon: Activity, run: () => select('run') },
    { id: 'tab-health', label: 'Graph health', group: 'Go to', Icon: HeartPulse, run: () => select('health') },
    { id: 'tab-knowledge', label: 'Knowledge graph', group: 'Go to', Icon: Waypoints, run: () => select('knowledge') },
    ...KNOWLEDGE_VIEWS.map(({ id, label, Icon }): Command => ({
      id: `knowledge-${id}`, label, group: 'Knowledge', Icon, run: () => showKnowledgeView(id),
    })),
  );
  if (copyLog) {
    commands.push({ id: 'copy-log', label: 'Copy the log', group: 'Run', Icon: Copy, run: copyLog });
  }
  commands.push(
    { id: 'theme-light', label: 'Use the light theme', group: 'Theme', Icon: Sun, run: () => setThemeChoice('light') },
    { id: 'theme-dark', label: 'Use the dark theme', group: 'Theme', Icon: Moon, run: () => setThemeChoice('dark') },
    { id: 'theme-system', label: 'Use the system theme', group: 'Theme', Icon: Monitor, run: () => setThemeChoice('system') },
    { id: 'shortcuts', label: 'Keyboard shortcuts', group: 'Panel', Icon: Keyboard, run: showShortcuts },
    { id: 'log-out', label: 'Log out', group: 'Panel', Icon: LogOut, run: signOut },
  );
  return commands;
}

/** The commands whose label or group contains every word of the query, those whose label starts with it first. */
export function matchCommands(commands: Command[], query: string): Command[] {
  const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const matches = commands.filter((command) => {
    const text = `${command.label} ${command.group}`.toLowerCase();
    return words.every((word) => text.includes(word));
  });
  const first = words[0];
  if (first === undefined) {
    return matches;
  }
  return [
    ...matches.filter((command) => command.label.toLowerCase().startsWith(first)),
    ...matches.filter((command) => !command.label.toLowerCase().startsWith(first)),
  ];
}
