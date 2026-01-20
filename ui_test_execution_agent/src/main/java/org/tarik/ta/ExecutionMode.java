/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta;

/**
 * Defines the execution modes for the UI test agent.
 */
public enum ExecutionMode {
    /**
     * Fully supervised mode where all actions are confirmed by the operator.
     * The agent will prompt for confirmation after locating elements.
     * The operator must create new elements on her own.
     * The operator can choose any of available actions at any interaction moment.
     */
    ATTENDED,

    /**
     * Semi-attended mode where the agent operates autonomously but allows
     * the operator to halt execution at any time through a countdown popup.
     * On halt, errors, or verification failures, the operator is notified
     * and only then she can choose any of available actions.
     */
    SEMI_ATTENDED,

    /**
     * Fully autonomous mode with no user interaction.
     * The agent retries on failures and only stops on terminal errors.
     */
    UNATTENDED
}