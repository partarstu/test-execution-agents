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
import { Monitor, Moon, Settings2, Sun } from 'lucide-react';
import { useState } from 'react';
import { enableNotify, notificationsSupported } from './attention';
import { setThemeChoice, useThemeChoice, type ThemeChoice } from './colorScheme';
import { Popover } from './kit/Popover';

const THEMES: { id: ThemeChoice; label: string; Icon: typeof Sun }[] = [
  { id: 'system', label: 'Same as the system', Icon: Monitor },
  { id: 'light', label: 'Light', Icon: Sun },
  { id: 'dark', label: 'Dark', Icon: Moon },
];

interface Props {
  notify: boolean;
  onNotifyChange: (notify: boolean) => void;
}

/** The operator's own settings for this browser: the theme and the desktop notification when the run starts waiting. */
export function PreferencesMenu({ notify, onNotifyChange }: Props) {
  const theme = useThemeChoice();
  const [blocked, setBlocked] = useState(false);

  const toggleNotify = async (enabled: boolean) => {
    const granted = await enableNotify(enabled);
    setBlocked(enabled && !granted);
    onNotifyChange(granted);
  };

  return (
    <Popover label={<><Settings2 size={18} /><span className="appbar-label">Preferences</span></>}>
      <div className="preferences">
        <fieldset>
          <legend>Theme</legend>
          {THEMES.map(({ id, label, Icon }) => (
            <label key={id} className="check">
              <input type="radio" name="panel-theme" checked={theme === id} onChange={() => setThemeChoice(id)} />
              <Icon size={16} />
              {label}
            </label>
          ))}
        </fieldset>
        {notificationsSupported() && (
          <fieldset>
            <legend>Notifications</legend>
            <label className="check">
              <input type="checkbox" checked={notify} onChange={(event) => void toggleNotify(event.target.checked)} />
              Notify me when the run waits and this page is not in front
            </label>
            {blocked && <p className="field-hint" role="status">The browser blocks notifications for this page; allow them in its site settings.</p>}
          </fieldset>
        )}
      </div>
    </Popover>
  );
}
