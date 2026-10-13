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
import { defineConfig } from 'vitest/config';
import react, { reactCompilerPreset } from '@vitejs/plugin-react';
import babel from '@rolldown/plugin-babel';
import type { Plugin } from 'vite';

const LAYER_ORDER_SHEET = '/layers.css';

/** Fails the build unless the page links the layer order before the bundle's sheets, whose chunks come in no fixed order. */
function layerOrderFirst(): Plugin {
  return {
    name: 'layer-order-first',
    apply: 'build',
    enforce: 'post',
    generateBundle(_options, bundle) {
      const page = bundle['index.html'];
      const html = page?.type === 'asset' ? String(page.source) : '';
      const first = /<link rel="stylesheet"[^>]*href="([^"]+)"/.exec(html)?.[1];
      if (first !== LAYER_ORDER_SHEET) {
        this.error(`index.html links ${first ?? 'no stylesheet'} first instead of ${LAYER_ORDER_SHEET}, so the layers would rank by chunk order`);
      }
    },
  };
}

export default defineConfig({
  plugins: [react(), babel({ presets: [reactCompilerPreset()] }), layerOrderFirst()],
  // Vitest empties CSS imports unless included; tokens.test.ts checks the contrast of the token values in the stylesheet.
  test: { css: { include: /\/tokens\.css/ } },
  build: {
    outDir: '../../../target/classes/panel',
    // Vite empties an output directory outside its root only when told to; stale hashed chunks would ship in the jar.
    emptyOutDir: true,
    license: { fileName: 'licenses.md' },
  },
});
