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
package org.tarik.ta.panel;

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.dto.ExecutionItemContext;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.SimilarChildrenService;
import org.tarik.ta.knowledge_graph.service.SuggestionService;
import org.tarik.ta.knowledge_graph.service.SuggestionService.SuggestionContext;
import org.tarik.ta.knowledge_graph.service.UiElementResolutionService;

/**
 * The execution item a procedure editor is opened for and the services it works with. They are passed per call because they live
 * in the run's request scope, which the singleton {@link OperatorPrompts} cannot inject.
 */
public record ProcedureEditorSession(@NotNull ExecutionItemContext itemContext,
                                     @NotNull SuggestionContext suggestionContext,
                                     @NotNull KnowledgeService knowledgeService,
                                     @NotNull SuggestionService suggestionService,
                                     @NotNull SimilarChildrenService similarChildrenService,
                                     @NotNull UiElementResolutionService elementResolutionService) {
}
