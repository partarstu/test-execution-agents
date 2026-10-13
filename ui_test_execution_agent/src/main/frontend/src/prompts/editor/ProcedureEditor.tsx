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
import { CircleAlert, OctagonX, Save, Trash2, Undo2 } from 'lucide-react';
import { Activity, startTransition, useEffect, useEffectEvent, useReducer, useRef, useState, ViewTransition } from 'react';
import { focusViewStart } from '../../kit/focus';
import { Modal } from '../../kit/Modal';
import type { BodyProps } from '../body';
import { ItemContext } from '../bodies/ItemContext';
import type { ChildRow, EditorNode, ParentSummary, Suggestions } from '../Prompt';
import { CaptureView } from './CaptureView';
import { EffectPicker } from './EffectPicker';
import { ElementInfoView } from './ElementInfoView';
import { ElementLookup } from './ElementLookup';
import {
  childRef, formAt, formsReducer, initialForms, isAnyDirty, type Forms, levelsBefore, nodeOfNewChild, saveProblem, toSavedNode,
} from './forms';
import { formPathOf, INITIAL_NAVIGATION, navigate, ROOT, viewKey, type EditorView, type NavigationAction } from './navigation';
import { ParentChooser } from './ParentChooser';
import { ProcedureForm, type EditorEnv } from './ProcedureForm';
import './editor.css';

type Dialog =
  | { kind: 'cancel' }
  | { kind: 'delete'; path: number[] }
  | { kind: 'no-element'; path: number[] }
  | { kind: 'suggestions'; path: number[] }
  | { kind: 'leave'; path: number[] }
  | { kind: 'discard-for-parent' };

/**
 * The procedure editor: one prompt with a stack of views. Child steps are saved into the knowledge as the operator saves them
 * here, and only Save and Cancel of the edited procedure end the prompt.
 */
export function ProcedureEditor({ prompt, answer, call, pending, searchDelayMillis }: BodyProps<'procedure-editor'>) {
  const { create, item, node } = prompt.payload;
  const [navigation, dispatchNavigation] = useReducer(navigate, INITIAL_NAVIGATION);
  const [forms, dispatchForms] = useReducer(formsReducer, node, initialForms);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const views = useRef<HTMLDivElement>(null);
  const invokers = useRef<(Element | null)[]>([]);
  const pendingFocus = useRef<NavigationAction['type'] | null>(null);
  const env: EditorEnv = { promptId: prompt.id, call, pending, searchDelayMillis };

  const go = (action: NavigationAction) => {
    setProblem(null);
    if (action.type === 'push') {
      invokers.current.push(document.activeElement);
    }
    pendingFocus.current = action.type;
    startTransition(() => dispatchNavigation(action));
  };

  // A new view hides the one holding the focus, so the focus moves into it, and back to the control that opened a view it leaves
  useEffect(() => {
    const moved = pendingFocus.current;
    pendingFocus.current = null;
    if (moved === null) {
      return;
    }
    const invoker = moved === 'pop' ? invokers.current.pop() : null;
    if (invoker instanceof HTMLElement && invoker.isConnected) {
      invoker.focus();
    } else {
      focusViewStart(views.current?.lastElementChild);
    }
  }, [navigation]);
  const edit = (path: number[], next: EditorNode) => dispatchForms({ type: 'edit', path, node: next });

  const checkSimilar = async (path: number[], subject: EditorNode) => {
    if (!subject.children.some((row) => row.id === null)) {
      return;
    }
    const response = await call('similar-children', { procedureId: subject.id, rows: subject.children.map(childRef) });
    if (response) {
      dispatchForms({ type: 'similar', path, similar: subject.children.map((_, index) => response.indices.includes(index)) });
    }
  };
  const checkInitialSimilar = useEffectEvent(() => checkSimilar([], node));
  useEffect(() => {
    void checkInitialSimilar();
  }, []);

  const loadSuggestions = async (path: number[], base: EditorNode) => {
    const suggestions = await call('load-suggestions', {
      description: base.fields.description, precedingSteps: levelsBefore(forms, path).flat(),
    });
    if (suggestions) {
      const next = withSuggestions(base, suggestions);
      edit(path, next);
      void checkSimilar(path, next);
    }
  };

  const openChild = async (parentPath: number[], index: number, row: ChildRow) => {
    const path = [...parentPath, index];
    if (row.id === null) {
      const blank = nodeOfNewChild(row);
      dispatchForms({ type: 'open', path, node: blank });
      go({ type: 'push', view: { kind: 'child', path } });
      void loadSuggestions(path, blank);
      return;
    }
    const loaded = await call('open-procedure', { procedureId: row.id });
    if (loaded) {
      dispatchForms({ type: 'open', path, node: loaded });
      go({ type: 'push', view: { kind: 'child', path } });
    }
  };

  const save = (path: number[], elementConfirmed = false) => {
    const subject = formAt(forms, path).node;
    const blocker = saveProblem(subject);
    if (blocker) {
      setProblem(blocker);
      return;
    }
    if (subject.fields.atomic && !subject.targetElement && !elementConfirmed) {
      setDialog({ kind: 'no-element', path });
      return;
    }
    if (path.length === 0) {
      answer({ outcome: 'SAVE', node: toSavedNode(subject) });
    } else {
      void saveChild(path, subject);
    }
  };

  const saveChild = async (path: number[], subject: EditorNode) => {
    const row = await call('save-child', toSavedNode(subject));
    if (row) {
      replaceRow(path, row);
      leave(path);
    }
  };

  const replaceRow = (path: number[], row: ChildRow | null) => {
    const parentPath = path.slice(0, -1);
    const index = path[path.length - 1];
    const parent = formAt(forms, parentPath);
    const children = row === null
      ? parent.node.children.filter((_, at) => at !== index)
      : parent.node.children.map((existing, at) => (at === index ? row : existing));
    edit(parentPath, { ...parent.node, children });
    const similar = row === null ? parent.similar.filter((_, at) => at !== index) : parent.similar.map((flag, at) => at !== index && flag);
    dispatchForms({ type: 'similar', path: parentPath, similar });
  };

  const leave = (path: number[]) => {
    dispatchForms({ type: 'close', path });
    go({ type: 'pop' });
  };

  const deleteProcedure = async (path: number[]) => {
    setDialog(null);
    const subject = formAt(forms, path).node;
    if (path.length === 0) {
      answer({ outcome: 'DELETE', procedureId: subject.id! });
    } else if (await call('delete-procedure', { procedureId: subject.id! })) {
      replaceRow(path, null);
      leave(path);
    }
  };

  const editParent = async () => {
    setDialog(null);
    const response = await call('edit-parent', { procedureId: formAt(forms, []).node.id! });
    if (!response) {
      return;
    }
    if (response.parents.length === 1) {
      await openParent(response.parents[0], false);
    } else {
      go({ type: 'push', view: { kind: 'parentChooser', parents: response.parents } });
    }
  };

  const openParent = async (parent: ParentSummary, fromChooser: boolean) => {
    const loaded = await call('open-procedure', { procedureId: parent.id });
    if (loaded) {
      dispatchForms({ type: 'reset', node: loaded });
      go(fromChooser ? { type: 'pop' } : { type: 'replace', view: ROOT });
      void checkSimilar([], loaded);
    }
  };

  const confirmDialog = () => {
    if (!dialog) {
      return;
    }
    setDialog(null);
    switch (dialog.kind) {
      case 'cancel':
        answer({ outcome: 'CANCEL' });
        break;
      case 'delete':
        void deleteProcedure(dialog.path);
        break;
      case 'no-element':
        save(dialog.path, true);
        break;
      case 'suggestions':
        void loadSuggestions(dialog.path, formAt(forms, dialog.path).node);
        break;
      case 'leave':
        leave(dialog.path);
        break;
      case 'discard-for-parent':
        void editParent();
        break;
    }
  };

  const renderView = (view: EditorView) => {
    const path = formPathOf(view);
    const back = () => go({ type: 'pop' });
    switch (view.kind) {
      case 'procedure':
      case 'child': {
        const form = formAt(forms, path);
        if (!form) {
          return null;
        }
        return (
          <ProcedureForm
            path={path}
            form={form}
            env={env}
            onEdit={(next) => edit(path, next)}
            onOpenChild={(index, row) => void openChild(path, index, row)}
            onNavigate={(next) => go({ type: 'push', view: next })}
            onSuggest={() => setDialog({ kind: 'suggestions', path })}
            footer={view.kind === 'procedure' ? rootFooter(form.node) : childFooter(path, form.node, form.dirty)}
          />
        );
      }
      case 'effectPicker': {
        const form = formAt(forms, path);
        return (
          <EffectPicker levels={levelsBefore(forms, path)} env={env} onBack={back} onPick={(effect) => {
            edit(path, { ...form.node, fields: { ...form.node.fields, prerequisites: [...form.node.fields.prerequisites, effect] } });
            back();
          }} />
        );
      }
      case 'elementLookup': {
        const form = formAt(forms, path);
        return (
          <ElementLookup procedureDescription={form.node.fields.description} testData={form.node.fields.testData} env={env} onBack={back}
            onChoose={(element) => {
              edit(path, { ...form.node, targetElement: element });
              back();
            }} />
        );
      }
      case 'elementInfo':
      case 'capture': {
        const form = formAt(forms, path);
        const element = form.node.targetElement;
        if (!element) {
          return null;
        }
        const onSaved = (saved: typeof element) => {
          edit(path, { ...form.node, targetElement: saved });
          back();
        };
        return view.kind === 'elementInfo'
          ? <ElementInfoView element={element} env={env} onSaved={onSaved} onBack={back} />
          : <CaptureView element={element} env={env} onSaved={onSaved} onBack={back} />;
      }
      case 'parentChooser':
        return <ParentChooser parents={view.parents} pending={pending} onBack={back} onChoose={(parent) => void openParent(parent, true)} />;
    }
  };

  const rootFooter = (root: EditorNode) => (
    <div className="answers">
      <button type="button" className="primary" disabled={pending} onClick={() => save([])}>
        <Save size={16} />
        {create ? 'Save the procedure' : 'Save the changes'}
      </button>
      {root.id && root.parents.length > 0 && (
        <button type="button" disabled={pending}
          onClick={() => (formAt(forms, []).dirty ? setDialog({ kind: 'discard-for-parent' }) : void editParent())}>
          Edit a parent instead
        </button>
      )}
      <button type="button" disabled={pending} onClick={() => (isAnyDirty(forms) ? setDialog({ kind: 'cancel' }) : answer({ outcome: 'CANCEL' }))}>
        <OctagonX size={16} />
        Cancel
      </button>
      {root.id && (
        <button type="button" className="danger answers-end" disabled={pending} onClick={() => setDialog({ kind: 'delete', path: [] })}>
          <Trash2 size={16} />
          Delete
        </button>
      )}
    </div>
  );

  const childFooter = (path: number[], child: EditorNode, dirty: boolean) => (
    <div className="answers">
      <button type="button" className="primary" disabled={pending} onClick={() => save(path)}><Save size={16} />Save the step</button>
      <button type="button" disabled={pending} onClick={() => (dirty ? setDialog({ kind: 'leave', path }) : leave(path))}>
        <Undo2 size={16} />
        Back to the parent
      </button>
      {child.id && (
        <button type="button" className="danger answers-end" disabled={pending} onClick={() => setDialog({ kind: 'delete', path })}>
          <Trash2 size={16} />
          Delete
        </button>
      )}
    </div>
  );

  const crumbs = navigation.stack.filter((view) => view.kind === 'procedure' || view.kind === 'child')
    .map((view) => formAt(forms, formPathOf(view))?.node.fields.description || 'New step');

  return (
    <div className="editor">
      <ItemContext item={item} />
      <nav aria-label="Where you are in the procedure">
        <ol className="breadcrumb">
          {crumbs.map((crumb, index) => <li key={index} aria-current={index === crumbs.length - 1 ? 'step' : undefined}>{crumb}</li>)}
        </ol>
      </nav>
      {problem && <p className="field-error" role="alert"><CircleAlert size={18} />{problem}</p>}
      <div ref={views} className="editor-views">
        {navigation.stack.map((view, index) => (
          <Activity key={`${viewKey(view)}-${index}`} mode={index === navigation.stack.length - 1 ? 'visible' : 'hidden'}>
            <ViewTransition>
              <div className="editor-view">{renderView(view)}</div>
            </ViewTransition>
          </Activity>
        ))}
      </div>
      <EditorDialog dialog={dialog} forms={forms} onConfirm={confirmDialog} onClose={() => setDialog(null)} />
    </div>
  );
}

function withSuggestions(base: EditorNode, suggestions: Suggestions): EditorNode {
  const children: ChildRow[] = suggestions.childSteps.map((description) => ({
    id: null, description, atomic: true, precondition: base.precondition, effects: [], negatedEffects: [], screenshotId: null,
  }));
  return {
    ...base,
    children,
    fields: {
      ...base.fields,
      atomic: suggestions.atomic,
      prerequisites: suggestions.prerequisites,
      effects: suggestions.effects,
      negatedEffects: suggestions.negatedEffects,
      expectedResults: base.precondition ? base.fields.expectedResults : suggestions.expectedResults,
    },
  };
}

interface DialogProps {
  dialog: Dialog | null;
  forms: Forms;
  onConfirm: () => void;
  onClose: () => void;
}

/** The questions the editor asks before it discards, deletes or replaces anything. */
function EditorDialog({ dialog, forms, onConfirm, onClose }: DialogProps) {
  if (!dialog) {
    return null;
  }
  const question = questionOf(dialog, forms);
  return (
    <Modal open title={question.title} onCancel={onClose}>
      {question.body}
      <div className="answers">
        <button type="button" className={question.danger ? 'danger' : 'primary'} onClick={onConfirm}>{question.confirm}</button>
        <button type="button" data-autofocus onClick={onClose}>{question.keep}</button>
      </div>
    </Modal>
  );
}

function questionOf(dialog: Dialog, forms: Forms) {
  switch (dialog.kind) {
    case 'cancel':
      return {
        title: 'Discard your changes?', confirm: 'Discard them', keep: 'Keep editing', danger: true,
        body: <p className="prose">The run goes back to asking what to do next. Steps you already saved stay in the knowledge.</p>,
      };
    case 'leave':
    case 'discard-for-parent':
      return {
        title: 'Discard your changes?', confirm: 'Discard them', keep: 'Keep editing', danger: true,
        body: <p className="prose">The changes to this procedure are lost.</p>,
      };
    case 'no-element':
      return {
        title: 'Save without a target element?', confirm: 'Save anyway', keep: 'Choose an element', danger: false,
        body: <p className="prose">Without a linked element the agent decides at run time what this action targets.</p>,
      };
    case 'suggestions':
      return {
        title: 'Replace the values with AI suggestions?', confirm: 'Replace them', keep: 'Keep my values', danger: true,
        body: <p className="prose">The type, the child steps, the prerequisites, the effects and the expected results are replaced.</p>,
      };
    case 'delete': {
      const subject = formAt(forms, dialog.path).node;
      const used = subject.parents.length > 0 || subject.testCases.length > 0;
      return {
        title: 'Delete the procedure?', confirm: used ? 'Delete it anyway' : 'Delete it', keep: 'Keep it', danger: true,
        body: (
          <>
            <p className="prose">Only “{subject.fields.description}” is deleted; its child procedures are kept. This cannot be undone.</p>
            {subject.parents.length > 0 && (
              <>
                <p>It is part of:</p>
                <ul className="plain-list">{subject.parents.map((parent, index) => <li key={index}>{parent}</li>)}</ul>
              </>
            )}
            {subject.testCases.length > 0 && (
              <>
                <p>Test cases use it:</p>
                <ul className="plain-list">{subject.testCases.map((testCase, index) => <li key={index}>{testCase}</li>)}</ul>
              </>
            )}
          </>
        ),
      };
    }
  }
}
