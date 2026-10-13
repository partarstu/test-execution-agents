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
import { CircleAlert, Eye, EyeOff, LoaderCircle } from 'lucide-react';
import { useActionState, useId, useState } from 'react';
import { login } from './api';
import { DisplacedNotice } from './DisplacedNotice';
import { IconButton } from './kit/IconButton';
import { SignalMark } from './kit/SignalMark';
import type { Displaced, SessionInfo } from './types';
import './LoginView.css';

const ERRORS: Record<string, string> = {
  rejected: 'Wrong password. Use the panel password of this deployment.',
  'rate-limited': 'Too many attempts. Wait a minute, then try again.',
  failed: 'The agent does not answer. Check that it is running, then try again.',
};

interface Props {
  notice: Displaced | null;
  onLoggedIn: (info: SessionInfo) => void;
}

export function LoginView({ notice, onLoggedIn }: Props) {
  const [revealed, setRevealed] = useState(false);
  const passwordId = useId();
  const errorId = useId();
  const [error, submit, pending] = useActionState(async (_previous: string | null, form: FormData) => {
    const result = await login(String(form.get('password') ?? ''));
    if (result.kind === 'ok') {
      onLoggedIn(result.info);
      return null;
    }
    return ERRORS[result.kind];
  }, null);

  return (
    <main className="login">
      {notice && <DisplacedNotice displaced={notice} />}
      <form className="login-form card" action={submit}>
        <div className="login-head">
          <SignalMark />
          <div>
            <h1>Operator panel</h1>
            <p className="muted">Watch and steer the UI test agent.</p>
          </div>
        </div>
        <label htmlFor={passwordId}>Panel password</label>
        <div className="password-field">
          <input id={passwordId} name="password" type={revealed ? 'text' : 'password'} autoComplete="current-password" required autoFocus
            aria-invalid={error ? true : undefined} aria-describedby={error ? errorId : undefined} />
          <IconButton icon={revealed ? EyeOff : Eye} label="Show password" small aria-pressed={revealed} onClick={() => setRevealed(!revealed)} />
        </div>
        {error && (
          <p id={errorId} className="form-error alert" role="alert">
            <CircleAlert size={18} />
            {error}
          </p>
        )}
        <button type="submit" className="primary" disabled={pending} aria-busy={pending}>
          {pending && <LoaderCircle size={18} className="spin" />}
          {pending ? 'Logging in…' : 'Log in'}
        </button>
      </form>
    </main>
  );
}
