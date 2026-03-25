/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.user_dialogs.knowledge;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;


/**
 * Immutable configuration record for {@link ProcedureKnowledgeCollectionDialog}.
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
        @NotNull UiElementDialogHelper uiElementDialogHelper) {}
