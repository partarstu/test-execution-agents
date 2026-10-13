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
import { ArrowLeft, OctagonX, Play, Plus, RefreshCw, RotateCcw, Search, SkipForward, Undo2, type LucideIcon } from 'lucide-react';
import { useState } from 'react';
import { Modal } from '../../kit/Modal';
import { ProcedureList } from '../../kit/ProcedureList';
import { AnswerOptions } from '../AnswerOptions';
import type { BodyProps } from '../body';
import type { SelectionAction } from '../Prompt';

const ACTION_ICONS: Record<SelectionAction, LucideIcon> = {
  RETRY: RefreshCw,
  RERUN_TEST_STEP: RotateCcw,
  RERUN_ATOMIC_PROCEDURE: Undo2,
  CREATE: Plus,
  BROWSE: Search,
  CANCEL: OctagonX,
  PROCEED: Play,
  CONTINUE: SkipForward,
};

const ACTION_HINTS: Record<SelectionAction, string> = {
  RETRY: 'Look for a matching procedure once more',
  RERUN_TEST_STEP: 'Run it again from its start',
  RERUN_ATOMIC_PROCEDURE: 'Repeat only the last UI action',
  CREATE: 'Teach the agent the steps for it',
  BROWSE: 'Choose one yourself from the knowledge graph',
  CANCEL: 'Stop the run with an error result',
  PROCEED: 'Execute the step you halted on',
  CONTINUE: 'Keep this step’s result and move on',
};

function actionLabel(action: SelectionAction, precondition: boolean): string {
  switch (action) {
    case 'RETRY':
      return 'Search the knowledge again';
    case 'RERUN_TEST_STEP':
      return precondition ? 'Rerun the precondition' : 'Rerun the test step';
    case 'RERUN_ATOMIC_PROCEDURE':
      return 'Rerun the last action';
    case 'CREATE':
      return 'Create a procedure';
    case 'BROWSE':
      return 'Browse existing procedures';
    case 'CANCEL':
      return 'Terminate the run';
    case 'PROCEED':
      return 'Proceed with this step';
    case 'CONTINUE':
      return 'Go on with the next step';
  }
}

/** Asks how to go on when no procedure fits the item or the run failed or was halted; Browse searches inside the prompt. */
export function NextActionBody({ prompt, answer, call, pending, searchDelayMillis }: BodyProps<'next-action'>) {
  const { message, itemDescription, precondition, actions } = prompt.payload;
  const [browsing, setBrowsing] = useState(false);
  const [confirmTerminate, setConfirmTerminate] = useState(false);

  const choose = (action: SelectionAction) => {
    if (action === 'BROWSE') {
      setBrowsing(true);
    } else if (action === 'CANCEL') {
      setConfirmTerminate(true);
    } else {
      answer({ action });
    }
  };

  return (
    <>
      <p className="prose">{message}</p>
      {browsing ? (
        <section className="subview" aria-label="Browse existing procedures">
          <button type="button" className="back" onClick={() => setBrowsing(false)}><ArrowLeft size={16} />Back to the choices</button>
          <ProcedureList
            search={async (query, showAllTypes) => (await call('search-procedures', {
              query, showAllTypes, procedureId: null, precondition, childIds: [],
            }))?.hits ?? null}
            precondition={precondition}
            initialQuery={itemDescription}
            searchDelayMillis={searchDelayMillis}
            pickLabel="Use this one"
            onPick={(hit) => answer({ action: 'BROWSE', procedureId: hit.id })}
            disabled={pending}
          />
        </section>
      ) : (
        <AnswerOptions
          disabled={pending}
          options={actions.map((action, index) => ({
            id: action,
            label: actionLabel(action, precondition),
            hint: ACTION_HINTS[action],
            Icon: ACTION_ICONS[action],
            tone: action === 'CANCEL' ? 'danger' : index === 0 ? 'primary' : undefined,
            onChoose: () => choose(action),
          }))}
        />
      )}
      <Modal open={confirmTerminate} title="Terminate the run?" onCancel={() => setConfirmTerminate(false)}>
        <p className="prose">The run stops here and the orchestrator receives an error result for “{itemDescription}”.</p>
        <div className="answers">
          <button type="button" className="danger" onClick={() => answer({ action: 'CANCEL' })}>Terminate the run</button>
          <button type="button" data-autofocus onClick={() => setConfirmTerminate(false)}>Keep it running</button>
        </div>
      </Modal>
    </>
  );
}
