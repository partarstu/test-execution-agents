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
import { useEffect, useId, useRef, type ReactNode } from 'react';
import './Modal.css';

/** A dialog shows its `title` as its heading, or is named by a heading of its content (`labelledBy`) that it does not repeat. */
type Props = {
  open: boolean;
  /** The safe answer Escape gives; without one, Escape does nothing. */
  onCancel?: () => void;
  wide?: boolean;
  /** Dims the page only lightly and, on a wide window, sits at its bottom, for a question about what the page shows. */
  seeThrough?: boolean;
  children: ReactNode;
} & ({ title: string; labelledBy?: never } | { labelledBy: string; title?: never });

/**
 * A blocking question on the native `<dialog>`, which provides the top layer, the inert background, the focus trap and the focus
 * return. A dialog without a safe answer cannot be dismissed: it reopens if the browser closes it without an answer.
 */
export function Modal({ open, title, labelledBy, onCancel, wide = false, seeThrough = false, children }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const answeredBeforeClose = useRef(false);
  const titleId = useId();

  useEffect(() => {
    const element = dialog.current;
    if (!element) {
      return;
    }
    if (open && !element.open) {
      element.showModal();
      // showModal moves the focus after React's autoFocus has run, so the control to focus is marked instead
      element.querySelector<HTMLElement>('[data-autofocus]')?.focus();
    } else if (!open && element.open) {
      element.close();
    }
  }, [open]);

  return (
    <dialog
      ref={dialog}
      className={['modal', wide ? 'modal-wide' : null, seeThrough ? 'modal-see-through' : null].filter(Boolean).join(' ')}
      aria-labelledby={labelledBy ?? titleId}
      onCancel={(event) => {
        event.preventDefault();
        answeredBeforeClose.current = !event.cancelable;
        onCancel?.();
      }}
      onClose={() => {
        // Without user activation since the last close request, Chrome closes the dialog on Escape after a non-cancelable cancel
        // event or without any, so a dialog closed while still open is shown again and gets the answer Escape gives, once
        const element = dialog.current;
        const answered = answeredBeforeClose.current;
        answeredBeforeClose.current = false;
        if (open && element?.isConnected && !element.open) {
          element.showModal();
          if (!answered) {
            onCancel?.();
          }
        }
      }}
    >
      {title !== undefined && <h2 id={titleId} className="modal-title">{title}</h2>}
      {children}
    </dialog>
  );
}
