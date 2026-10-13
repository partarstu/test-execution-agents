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
import { RotateCcw, TriangleAlert } from 'lucide-react';
import { Component, Fragment, type ReactNode } from 'react';

interface State {
  failed: boolean;
  attempt: number;
}

/** Keeps a view that fails to render to itself, so the rest of the panel and an open prompt keep working; a class, as React offers no hook for it. */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false, attempt: 0 };

  static getDerivedStateFromError(): Partial<State> {
    return { failed: true };
  }

  render() {
    if (!this.state.failed) {
      return <Fragment key={this.state.attempt}>{this.props.children}</Fragment>;
    }
    return (
      <div className="empty-state" role="alert">
        <TriangleAlert size={32} />
        <strong>This view failed</strong>
        <p>The rest of the panel keeps working. Show the view again, or reload the page if it keeps failing.</p>
        <button type="button" onClick={() => this.setState({ failed: false, attempt: this.state.attempt + 1 })}>
          <RotateCcw size={16} />
          Show it again
        </button>
      </div>
    );
  }
}
