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
package org.tarik.ta;

/**
 * Defines the execution modes for the UI test agent.
 */
public enum ExecutionMode {
    /**
     * Supervised mode where the agent operates autonomously but allows
     * the operator to halt execution at any time through a countdown popup.
     * On halt, errors, or verification failures, the operator is notified
     * and only then she can choose any of available actions.
     */
    SUPERVISED,

    /**
     * Fully autonomous mode with no user interaction.
     * The agent retries on failures and only stops on terminal errors.
     */
    UNATTENDED
}