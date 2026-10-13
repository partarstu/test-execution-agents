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
package org.tarik.ta.agents;

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.model.ImageContentCreator;

/**
 * Implemented by every agent which is sent a screenshot. The agent itself states which of the configured image settings are
 * its own, so that a caller holding the agent never has to know which settings belong to it.
 */
public interface VisionBasedAgent {

    /**
     * How this agent's screenshots are encoded for its model, according to the agent's configured image detail level and model
     * provider.
     */
    @NotNull
    ImageContentCreator getImageContentCreator(@NotNull UiTestAgentConfig config);
}
