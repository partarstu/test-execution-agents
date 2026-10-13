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
import type { ItemView } from '../Prompt';

/** The test step or precondition the run is executing, so the operator always sees what the question is about. */
export function ItemContext({ item }: { item: ItemView }) {
  const testData = item.testData.filter((value) => value.trim() !== '');
  return (
    <dl className="item-context">
      <dt>{item.precondition ? 'Current precondition' : 'Current test step'}</dt>
      <dd>{item.description}</dd>
      {testData.length > 0 && (
        <>
          <dt>Test data</dt>
          <dd>{testData.join(', ')}</dd>
        </>
      )}
    </dl>
  );
}
