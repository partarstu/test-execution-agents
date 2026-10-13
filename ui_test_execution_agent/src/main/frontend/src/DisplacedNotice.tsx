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
import { AppWindow, ShieldAlert } from 'lucide-react';
import { localTime } from './format';
import type { Displaced } from './types';
import './DisplacedNotice.css';

interface Props {
  displaced: Displaced;
  onUseHere?: () => void;
}

export function DisplacedNotice({ displaced, onUseHere }: Props) {
  if (displaced.reason === 'other-tab') {
    return (
      <section className="notice" role="status">
        <AppWindow size={22} className="notice-icon" />
        <div className="notice-body">
          <h2>This panel is open in another tab</h2>
          <p>
            Since {localTime(displaced.at)} another tab of this browser gets the updates. Only one tab gets them at a time.
          </p>
        </div>
        {onUseHere && (
          <button type="button" className="primary" onClick={onUseHere}>
            Use here
          </button>
        )}
      </section>
    );
  }
  return (
    <section className="notice notice-alert" role="alert">
      <ShieldAlert size={22} className="notice-icon" />
      <div className="notice-body">
        <h2>Another login took over this panel</h2>
        <p>
          At {localTime(displaced.at)} from {displaced.sourceIp}, with the browser “{displaced.browser}”.
        </p>
        <p>If this wasn't you, change the panel password.</p>
      </div>
    </section>
  );
}
