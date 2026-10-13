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
import { LoaderCircle, Unplug } from 'lucide-react';
import { useEffect, useState } from 'react';
import { probeSession } from './api';
import { SignalMark } from './kit/SignalMark';
import { LoginView } from './LoginView';
import { PanelView } from './PanelView';
import type { Displaced, SessionInfo } from './types';

type View =
  | { kind: 'loading' }
  | { kind: 'unreachable' }
  | { kind: 'login'; notice: Displaced | null }
  | { kind: 'panel'; info: SessionInfo };

export function App() {
  const [view, setView] = useState<View>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    void probeSession().then((probe) => {
      if (!active) {
        return;
      }
      if (probe.kind === 'active') {
        setView({ kind: 'panel', info: probe.info });
      } else if (probe.kind === 'none') {
        setView({ kind: 'login', notice: probe.displaced });
      } else {
        setView({ kind: 'unreachable' });
      }
    });
    return () => {
      active = false;
    };
  }, []);

  const showLogin = (notice: Displaced | null) => setView({ kind: 'login', notice });

  switch (view.kind) {
    case 'loading':
      return (
        <main className="splash" aria-busy="true">
          <h1 className="splash-title"><SignalMark />Operator panel</h1>
          <p className="loading-note"><LoaderCircle size={18} className="spin" />Connecting to the agent…</p>
        </main>
      );
    case 'unreachable':
      return (
        <main className="splash">
          <h1 className="splash-title"><SignalMark />Operator panel</h1>
          <div className="empty-state">
            <Unplug size={32} />
            <strong>The agent does not answer</strong>
            <p>Check that it is running, then reload this page.</p>
            <button type="button" className="primary" onClick={() => location.reload()}>Reload</button>
          </div>
        </main>
      );
    case 'login':
      return <LoginView notice={view.notice} onLoggedIn={(info) => setView({ kind: 'panel', info })} />;
    case 'panel':
      return <PanelView info={view.info} onSignedOut={showLogin} />;
  }
}
