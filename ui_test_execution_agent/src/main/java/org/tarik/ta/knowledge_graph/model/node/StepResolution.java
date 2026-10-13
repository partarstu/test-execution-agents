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
package org.tarik.ta.knowledge_graph.model.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.core.model.ContextEntry.StepType;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.MatchConfidence;

import java.util.List;
import java.util.UUID;

/**
 * How one precondition or test step of a run was resolved to a procedure: by the knowledge graph or by the operator, which
 * candidates the match offered with their scores, and how the item ended.
 */
public record StepResolution(@NotNull UUID id, int sequence, @NotNull StepType itemType, @NotNull String itemDescription,
                             @NotNull Source source, @Nullable MatchConfidence confidence, @NotNull List<Candidate> candidates,
                             boolean wasDemoted, boolean demotedDueToPrerequisites, @Nullable UUID chosenProcedureId,
                             @NotNull Outcome outcome, long durationMs) {
    public static final String LABEL = "StepResolution";
    public static final String PROP_ITEM_TYPE = "itemType";
    public static final String PROP_ITEM_DESCRIPTION = "itemDescription";
    public static final String PROP_SOURCE = "source";
    public static final String PROP_CONFIDENCE = "confidence";
    public static final String PROP_CANDIDATES = "candidates";
    public static final String PROP_WAS_DEMOTED = "wasDemoted";
    public static final String PROP_DEMOTED_DUE_TO_PREREQUISITES = "demotedDueToPrerequisites";
    public static final String PROP_OUTCOME = "outcome";
    public static final String PROP_DURATION_MS = "durationMs";
    public static final int MAX_ITEM_DESCRIPTION_LENGTH = 500;

    public StepResolution {
        candidates = List.copyOf(candidates);
        if (itemDescription.length() > MAX_ITEM_DESCRIPTION_LENGTH) {
            itemDescription = itemDescription.substring(0, MAX_ITEM_DESCRIPTION_LENGTH);
        }
    }

    public enum Source { KNOWLEDGE, OPERATOR_SELECTED, OPERATOR_CREATED, UNRESOLVED }

    public enum Outcome { SUCCEEDED, FAILED, NOT_EXECUTED }

    /** A procedure the match offered, with the values its re-ranking used. */
    public record Candidate(@NotNull UUID procedureId, @NotNull String description, double semanticScore, int satisfiedPrerequisites,
                            int totalPrerequisites, int sharedParentCount, boolean lowStability,
                            @NotNull List<String> unmetPrerequisites) {
        public Candidate {
            unmetPrerequisites = List.copyOf(unmetPrerequisites);
        }
    }
}
