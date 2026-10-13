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
import { Fragment } from 'react';
import { Modal } from './kit/Modal';
import { SHORTCUTS } from './shortcuts';

export function ShortcutsSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Modal open={open} title="Keyboard shortcuts" onCancel={onClose}>
      <dl className="shortcuts">
        {SHORTCUTS.map(({ keys, join, description }) => (
          <Fragment key={description}>
            <dt>
              {keys.map((key, index) => (
                <Fragment key={key}>
                  {index > 0 && <span className="shortcut-join">{join}</span>}
                  <kbd className="kbd">{key}</kbd>
                </Fragment>
              ))}
            </dt>
            <dd>{description}</dd>
          </Fragment>
        ))}
      </dl>
      <div className="answers">
        <button type="button" className="primary" data-autofocus onClick={onClose}>Close</button>
      </div>
    </Modal>
  );
}
