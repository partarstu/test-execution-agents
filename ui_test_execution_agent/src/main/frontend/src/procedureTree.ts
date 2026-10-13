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
import type { ReceivedPrompt } from './prompts/Prompt';
import type { ProcedureNode, ProcedureNodeStatus } from './types';

/** A node of the item's procedure tree; `leaf` numbers the atomic actions in execution order, `firstLeaf`/`lastLeaf` span its own. */
export interface TreeNode {
  index: number;
  node: ProcedureNode;
  children: TreeNode[];
  leaf: number | null;
  firstLeaf: number;
  lastLeaf: number;
}

export interface ProcedureTree {
  root: TreeNode;
  leaves: TreeNode[];
}

/** What a row shows: the node's status, or for the executing action whether a question about it waits for the operator. */
export type DisplayStatus = ProcedureNodeStatus | 'WAITING' | 'EXECUTED' | 'ATTENTION';

/** How the guide of a parent runs alongside a child: lit up to the current action, travelled, or still ahead. */
export type Guide = 'trunk' | 'path' | 'done' | 'ahead';

export function buildTree(procedures: ProcedureNode[]): ProcedureTree | null {
  const leaves: TreeNode[] = [];
  const open: TreeNode[] = [];
  let root: TreeNode | null = null;
  procedures.forEach((node, index) => {
    const treeNode: TreeNode = { index, node, children: [], leaf: null, firstLeaf: -1, lastLeaf: -1 };
    open.length = Math.min(open.length, node.depth);
    const parent = open.at(-1);
    if (parent) {
      parent.children.push(treeNode);
    } else {
      root ??= treeNode;
    }
    if (node.atomic) {
      treeNode.leaf = leaves.length;
      leaves.push(treeNode);
    }
    open.push(treeNode);
  });
  if (root === null) {
    return null;
  }
  span(root);
  return { root, leaves };
}

function span(treeNode: TreeNode): void {
  if (treeNode.leaf !== null) {
    treeNode.firstLeaf = treeNode.leaf;
    treeNode.lastLeaf = treeNode.leaf;
    return;
  }
  treeNode.children.forEach(span);
  treeNode.firstLeaf = treeNode.children[0]?.firstLeaf ?? -1;
  treeNode.lastLeaf = treeNode.children.at(-1)?.lastLeaf ?? -1;
}

export function leavesOf(tree: ProcedureTree, treeNode: TreeNode): TreeNode[] {
  return treeNode.firstLeaf < 0 ? [] : tree.leaves.slice(treeNode.firstLeaf, treeNode.lastLeaf + 1);
}

export function isFinished(status: ProcedureNodeStatus): boolean {
  return status === 'COMPLETED' || status === 'SKIPPED';
}

export function displayStatus(node: ProcedureNode, prompt: ReceivedPrompt | null): DisplayStatus {
  if (node.status !== 'RUNNING' || !node.atomic || prompt === null) {
    return node.status;
  }
  if (prompt.type === 'confirmation') {
    return prompt.payload.beforeExecution ? 'WAITING' : 'EXECUTED';
  }
  return 'ATTENTION';
}

/** The atomic action executing now; only one runs at a time. */
export function currentLeaf(tree: ProcedureTree): TreeNode | null {
  return tree.leaves.findLast((leaf) => leaf.node.status === 'RUNNING') ?? null;
}

/** The first action still to run, unless an action has failed and the run will not go on with it. */
export function nextLeaf(tree: ProcedureTree): TreeNode | null {
  if (tree.leaves.some((leaf) => leaf.node.status === 'FAILED')) {
    return null;
  }
  return tree.leaves.find((leaf) => leaf.node.status === 'PENDING') ?? null;
}

export function contains(treeNode: TreeNode, leaf: number): boolean {
  return treeNode.firstLeaf <= leaf && leaf <= treeNode.lastLeaf;
}

export function guideOf(child: TreeNode, parent: TreeNode, current: TreeNode | null, tree: ProcedureTree): Guide {
  if (current?.leaf != null && contains(parent, current.leaf)) {
    if (child.lastLeaf < current.leaf) {
      return 'trunk';
    }
    return contains(child, current.leaf) ? 'path' : 'ahead';
  }
  return leavesOf(tree, child).every((leaf) => isFinished(leaf.node.status)) ? 'done' : 'ahead';
}

/**
 * Whether a composite starts folded: the full tree folds the finished ones, the dense one beside the large live view everything off
 * the path to the current action.
 */
export function foldedByDefault(treeNode: TreeNode, current: TreeNode | null, dense: boolean): boolean {
  if (treeNode.leaf !== null || treeNode.index === 0) {
    return false;
  }
  if (dense) {
    return current?.leaf == null || !contains(treeNode, current.leaf);
  }
  return isFinished(treeNode.node.status);
}

export interface LeafCounts {
  done: number;
  skipped: number;
  total: number;
}

export function leafCounts(leaves: TreeNode[]): LeafCounts {
  return {
    done: leaves.filter((leaf) => leaf.node.status === 'COMPLETED').length,
    skipped: leaves.filter((leaf) => leaf.node.status === 'SKIPPED').length,
    total: leaves.length,
  };
}
