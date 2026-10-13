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
import { cleanup } from '@testing-library/react';
import axe from 'axe-core';
import { afterEach, beforeEach, vi } from 'vitest';

/** Gives jsdom the dialog and popover methods, scrollIntoView, matchMedia and ResizeObserver the panel uses, and unmounts every render after its test. */
export function installDom(): void {
  if (typeof HTMLDialogElement.prototype.showModal !== 'function') {
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.open = false;
    };
  }
  // The stubs mark a shown popover with data-popover-open, which jsdom cannot match as :popover-open
  if (typeof HTMLElement.prototype.showPopover !== 'function') {
    HTMLElement.prototype.showPopover = function showPopover(this: HTMLElement) {
      this.toggleAttribute('data-popover-open', true);
    };
    HTMLElement.prototype.hidePopover = function hidePopover(this: HTMLElement) {
      this.toggleAttribute('data-popover-open', false);
    };
  }
  if (typeof Element.prototype.scrollIntoView !== 'function') {
    Element.prototype.scrollIntoView = function scrollIntoView() {};
  }
  beforeEach(() => {
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: false, media: query, addEventListener() {}, removeEventListener() {} }));
    vi.stubGlobal('ResizeObserver', class {
      observe() {}
      disconnect() {}
    });
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });
}

/** Answers every fetch with the JSON the route gives for the request's path. */
export function stubFetch(route: (path: string) => unknown): void {
  vi.stubGlobal('fetch', vi.fn(async (input: string) => new Response(JSON.stringify(route(new URL(input, 'http://panel').pathname)))));
}

/**
 * The axe-core violations inside the rendered document; contrast is left to tokens.test.ts because jsdom has no layout, and
 * landmarks to the page shell a component renders into.
 */
export async function axeViolations(root: Element = document.body): Promise<string[]> {
  const results = await axe.run(root, { rules: { 'color-contrast': { enabled: false }, region: { enabled: false } } });
  return results.violations.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`);
}
