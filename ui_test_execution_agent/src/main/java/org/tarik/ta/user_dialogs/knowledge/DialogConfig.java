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
package org.tarik.ta.user_dialogs.knowledge;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.ProcedureUsageByTestCaseTrackingService;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;


/**
 * Immutable configuration record for {@link ProcedureDialog}.
 * Use {@code existingProcedure} to pre-fill all procedure fields at once; pass {@code null} to start with an empty form.
 */
record DialogConfig(
        String title,
        String headerMessage,
        @Nullable Procedure existingProcedure,
        @Nullable List<ChildProcedureInDialog> preloadedChildren,
        @Nullable UUID targetUiElementId,
        boolean showTestDataAndExpectedResults,
        boolean hasParent,
        boolean autoTriggerSuggestions,
        @Nullable ExecutionItemContext itemContext,
        @NotNull KnowledgeService knowledgeService,
        @NotNull KnowledgeIngestionService ingestionService,
        @Nullable UUID currentProcedureId,
        @Nullable SuggestionLoaderFactory childLoaderFactory,
        @Nullable BufferedImage preloadedElementScreenshot,
        @NotNull UiTestAgentConfig uiTestAgentConfig,
        @NotNull UiElementRepository uiElementRepository,
        @NotNull UiElementDialogHelper uiElementDialogHelper,
        @Nullable ProcedureUsageByTestCaseTrackingService usageTrackingService,
        @Nullable UUID originatingParentId) {}
