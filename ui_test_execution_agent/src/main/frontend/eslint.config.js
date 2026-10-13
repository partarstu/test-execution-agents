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
import babelParser from '@babel/eslint-parser';
import jsxA11y from 'eslint-plugin-jsx-a11y-x';
import reactHooks from 'eslint-plugin-react-hooks';
import { defineConfig } from 'eslint/config';

export default defineConfig([
  { ignores: ['node/'] },
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      // typescript-eslint needs the compiler API that TypeScript 7 no longer ships, so Babel parses the sources.
      parser: babelParser,
      parserOptions: {
        requireConfigFile: false,
        babelOptions: { babelrc: false, configFile: false, parserOpts: { plugins: ['jsx', 'typescript'] } },
      },
    },
  },
  reactHooks.configs.flat.recommended,
  // The ESLint 10 fork of eslint-plugin-jsx-a11y, whose own release still peers ESLint 9; only its rules, so Babel stays the parser
  {
    files: ['**/*.tsx'],
    plugins: jsxA11y.configs.recommended.plugins,
    rules: {
      ...jsxA11y.configs.recommended.rules,
      // Each autoFocus marks where the focus belongs in a view that has just replaced the focused one, or a prompt's default answer
      'jsx-a11y-x/no-autofocus': 'off',
      // A scrolling region must take the focus, or a keyboard cannot scroll it
      'jsx-a11y-x/no-noninteractive-tabindex': ['error', { tags: [], roles: ['tabpanel', 'region'] }],
    },
  },
  {
    rules: {
      'no-restricted-syntax': ['error', {
        selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
        message: 'Agent and LLM text is rendered through React escaping, never as HTML.',
      }],
    },
  },
]);
