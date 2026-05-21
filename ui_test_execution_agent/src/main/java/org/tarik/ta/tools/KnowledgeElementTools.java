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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;
import org.tarik.ta.knowledge_graph.service.FailureContextService;

import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.core.error.ErrorCategory.*;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;

/**
 * Collecting knowledge-flow tools for the knowledge persistence feature.
 * Provides tools for creating atomic procedures and linking them to UI
 * elements.
 */
@Singleton
class KnowledgeElementTools extends UiAbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeElementTools.class);

    private final ProcedureRepository procedureRepository;
    private final EmbeddingService embeddingService;
    private final FailureContextService failureContextService;

    @Inject
    KnowledgeElementTools(UiStateCheckAgent uiStateCheckAgent,
                          ProcedureRepository procedureRepository,
                          EmbeddingService embeddingService,
                          FailureContextService failureContextService) {
        super(uiStateCheckAgent);
        this.procedureRepository = requireNonNull(procedureRepository, "procedureRepository");
        this.embeddingService = requireNonNull(embeddingService, "embeddingService");
        this.failureContextService = requireNonNull(failureContextService, "failureContextService");
    }

    /**
     * Creates an atomic procedure and links it to the specified UI element via
     * a TARGETS relationship.
     */
    @Tool("Creates an atomic procedure with a TARGETS relationship to the specified UI element.")
    public DefineAtomicStepResult defineAtomicStepWithElement(
            @P("Description of the atomic procedure step") String stepDescription,
            @P("UUID of the UI element this step targets") UUID elementId,
            @P(value = "List of test data values relevant to this atomic step", required = false) List<String> testData,
            @P(value = "Description of expected results after executing this step", required = false) String expectedResults,
            @P("True if this atomic step is a part of the test precondition, False if it is a part of the test step") boolean isPreconditionStep) {
        if (isBlank(stepDescription)) {
            throw new ToolExecutionException("Step description cannot be empty", TRANSIENT_TOOL_ERROR);
        }
        requireNonNull(elementId, "elementId");

        try {
            var atomicStep = Procedure.createAtomic(
                    stepDescription,
                    testData != null ? testData : List.of(),
                    expectedResults != null ? expectedResults : "",
                    List.of(), List.of(), isPreconditionStep);
            var embedding = embeddingService.embed(stepDescription);
            procedureRepository.save(atomicStep, embedding.vector());
            procedureRepository.linkToUiElement(atomicStep.id(), elementId);
            LOG.info("Created atomic procedure '{}' (id={}) targeting UI element {}",
                    stepDescription, atomicStep.id(), elementId);
            return new DefineAtomicStepResult(true, atomicStep.id(),
                    "Atomic step created and linked to UI element successfully");
        } catch (Exception e) {
            throw rethrowAsToolException(e, "defining atomic step with UI element");
        }
    }

    @Tool("Retrieves known failure hints and resolutions for a procedure.")
    public List<String> queryProcedureFailureHints(@P("UUID of the procedure") UUID procedureId) {
        return failureContextService.findFailureHints(procedureId);
    }

    @Description("Result of creating an atomic procedure with a TARGETS relationship to a UI element.")
    record DefineAtomicStepResult(
            @Description("Whether the step was successfully created") boolean success,
            @Description("UUID of the created atomic procedure") UUID procedureId,
            @Description("Additional message") String message) {
    }
}
