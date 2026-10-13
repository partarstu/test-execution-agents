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
import type { NodeKind } from './model';
import { KIND_LABELS } from './theme';

const KINDS: NodeKind[] = ['COMPOSITE', 'ATOMIC', 'ELEMENT', 'TEST_CASE'];

/** The legend entries of the node kinds, shared by the views that colour nodes by their kind. */
export function KindLegendEntries() {
  return KINDS.map((kind) => (
    <li key={kind}>
      <span className={`kind-swatch kind-${kind.toLowerCase()}`} aria-hidden="true" />
      {KIND_LABELS[kind]}
    </li>
  ));
}
