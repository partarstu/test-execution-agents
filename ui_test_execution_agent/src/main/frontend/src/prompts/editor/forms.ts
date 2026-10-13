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
import { filled } from '../../kit/TextListField';
import type { ChildRef, ChildRow, EditorNode, SavedNode } from '../Prompt';
import { pathKey } from './navigation';

/** One open procedure form: what it holds, which of its child rows have a similar procedure, and whether it changed. */
export interface FormState {
  node: EditorNode;
  similar: boolean[];
  dirty: boolean;
}

/** The open forms by the path of their procedure, so a form keeps its input while the operator works on a child or a picker. */
export type Forms = Record<string, FormState>;

export type FormsAction =
  | { type: 'open'; path: number[]; node: EditorNode }
  | { type: 'edit'; path: number[]; node: EditorNode }
  | { type: 'similar'; path: number[]; similar: boolean[] }
  | { type: 'close'; path: number[] }
  | { type: 'reset'; node: EditorNode };

export function initialForms(node: EditorNode): Forms {
  return { [pathKey([])]: { node, similar: node.children.map(() => false), dirty: false } };
}

export function formsReducer(forms: Forms, action: FormsAction): Forms {
  switch (action.type) {
    case 'open':
      return { ...forms, [pathKey(action.path)]: { node: action.node, similar: action.node.children.map(() => false), dirty: false } };
    case 'edit': {
      const current = forms[pathKey(action.path)];
      return { ...forms, [pathKey(action.path)]: { ...current, node: action.node, dirty: true } };
    }
    case 'similar': {
      const current = forms[pathKey(action.path)];
      return { ...forms, [pathKey(action.path)]: { ...current, similar: action.similar } };
    }
    case 'close': {
      const prefix = pathKey(action.path);
      return Object.fromEntries(Object.entries(forms).filter(([key]) => key !== prefix && !key.startsWith(`${prefix}/`)));
    }
    case 'reset':
      return initialForms(action.node);
  }
}

export function formAt(forms: Forms, path: number[]): FormState {
  return forms[pathKey(path)];
}

export function isAnyDirty(forms: Forms): boolean {
  return Object.values(forms).some((form) => form.dirty);
}

export function childRef(row: ChildRow): ChildRef {
  return { id: row.id, description: row.description };
}

/** The preceding siblings at every nesting level above the form at `path`, as the server folds the effects before it. */
export function levelsBefore(forms: Forms, path: number[]): ChildRef[][] {
  return path.map((index, depth) => formAt(forms, path.slice(0, depth)).node.children.slice(0, index).map(childRef));
}

/** A blank form for a child row that is still only a suggested description. */
export function nodeOfNewChild(row: ChildRow): EditorNode {
  return {
    id: null,
    precondition: row.precondition,
    fields: {
      description: row.description, additionalInfo: null, atomic: true, optional: false, prerequisites: [], effects: [],
      negatedEffects: [], testData: [], expectedResults: '',
    },
    targetElement: null,
    children: [],
    parents: [],
    testCases: [],
  };
}

export function toSavedNode(node: EditorNode): SavedNode {
  const { fields } = node;
  return {
    id: node.id,
    precondition: node.precondition,
    fields: {
      ...fields,
      description: fields.description.trim(),
      additionalInfo: fields.additionalInfo?.trim() ? fields.additionalInfo.trim() : null,
      prerequisites: filled(fields.prerequisites),
      effects: filled(fields.effects),
      negatedEffects: filled(fields.negatedEffects),
      testData: filled(fields.testData),
    },
    targetElementId: fields.atomic ? node.targetElement?.id ?? null : null,
    childIds: fields.atomic ? [] : node.children.flatMap((row) => (row.id ? [row.id] : [])),
  };
}

/** Why the form cannot be saved yet, checked before asking the server; null when nothing stops it. */
export function saveProblem(node: EditorNode): string | null {
  if (node.fields.description.trim() === '') {
    return 'Enter a description of the procedure.';
  }
  if (node.fields.atomic) {
    return null;
  }
  if (node.children.length === 0) {
    return 'A composite procedure needs at least one child step.';
  }
  const unsaved = node.children.find((row) => row.id === null);
  return unsaved ? `Open the step “${unsaved.description}” and save it first.` : null;
}
