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
import { Brain, ChartColumn, History, Images, Network, type LucideIcon } from 'lucide-react';

// Apart from the lazily loaded tab, so the command palette can list the views without loading them
export type KnowledgeView = 'map' | 'elements' | 'brain' | 'trends' | 'failures';

export const KNOWLEDGE_VIEWS: { id: KnowledgeView; label: string; Icon: LucideIcon }[] = [
  { id: 'map', label: 'Map', Icon: Network },
  { id: 'elements', label: 'Elements', Icon: Images },
  { id: 'brain', label: 'Brain', Icon: Brain },
  { id: 'trends', label: 'Trends', Icon: ChartColumn },
  { id: 'failures', label: 'Failures', Icon: History },
];
