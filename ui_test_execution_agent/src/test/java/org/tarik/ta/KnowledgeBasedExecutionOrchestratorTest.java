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
package org.tarik.ta;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.knowledge_graph.KnowledgeBasedExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.KnowledgeServices;
import org.tarik.ta.knowledge_graph.service.*;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.tools.CommonTools;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBasedExecutionOrchestratorTest {

    @Mock
    private UiTestExecutionContext mockContext;
    @Mock
    private CommonTools mockCommonTools;
    @Mock
    private KnowledgeService mockKnowledgeService;
    @Mock
    private KnowledgeIngestionService mockIngestionService;
    @Mock
    private SatisfiesEdgeService mockSatisfiesEdgeService;
    @Mock
    private FailureContextService mockFailureContextService;
    @Mock
    private ProcedureUsageByTestCaseTrackingService mockProcedureUsageByTestCaseTrackingService;
    @Mock
    private UiTestStepVerificationAgent mockTestStepVerificationAgent;
    @Mock
    private UiPreconditionVerificationAgent mockPreconditionVerificationAgent;
    @Mock
    private UiTestStepActionAgent mockTestStepActionAgent;
    @Mock
    private UiPreconditionActionAgent mockPreconditionActionAgent;
    @Mock
    private KnowledgeSuggestionAgent mockKnowledgeSuggestionAgent;

    private KnowledgeServices mockKnowledgeServices;

    private MockedStatic<UiTestAgentConfig> configMock;
    private MockedStatic<KnowledgeServiceFactory> knowledgeServiceFactoryMock;

    @BeforeEach
    void setUp() {
        configMock = mockStatic(UiTestAgentConfig.class);
        knowledgeServiceFactoryMock = mockStatic(KnowledgeServiceFactory.class);

        configMock.when(UiTestAgentConfig::isFullyUnattended).thenReturn(true);
        knowledgeServiceFactoryMock.when(() -> KnowledgeServiceFactory.createKnowledgeIngestionService(any(), any(), any())).thenReturn(mockIngestionService);
        mockKnowledgeServices = new KnowledgeServices(mockKnowledgeService, mockIngestionService, mockSatisfiesEdgeService,
                mockProcedureUsageByTestCaseTrackingService, mockFailureContextService, (id, located, timeMs, strategy) -> {}, id -> Optional.empty());
    }

    @AfterEach
    void tearDown() {
        configMock.close();
        knowledgeServiceFactoryMock.close();
    }

    @Test
    @DisplayName("executeBasedOnKnowledge should throw exception when no match found in unattended mode")
    void executeBasedOnKnowledge_shouldThrow_whenNoMatchInUnattended() {
        TestCase testCase = new TestCase("test", List.of(), List.of(new TestStep("action", List.of(), "results")));
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.empty());

        // This should throw MissingProcedureException
        org.junit.jupiter.api.Assertions.assertThrows(org.tarik.ta.exceptions.MissingProcedureException.class, () -> {
            KnowledgeBasedExecutionOrchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, mockKnowledgeServices, mockCommonTools,
                    mockTestStepVerificationAgent, mockPreconditionVerificationAgent, mockTestStepActionAgent,
                    mockPreconditionActionAgent, mockKnowledgeSuggestionAgent);
        });
    }
}
