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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.agents.UiPreconditionActionAgent;
import org.tarik.ta.agents.UiPreconditionVerificationAgent;
import org.tarik.ta.agents.UiTestStepActionAgent;
import org.tarik.ta.agents.UiTestStepVerificationAgent;

import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.agents.UiElementBoundingBoxAgent;
import org.tarik.ta.agents.BestUiElementMatchSelectionAgent;
import org.tarik.ta.agents.DbUiElementSelectionAgent;
import org.tarik.ta.agents.UiElementExtendedDescriptionAgent;
import org.tarik.ta.agents.UiElementResolutionAgent;
import org.tarik.ta.agents.KnowledgeSuggestionAgent;
import org.tarik.ta.core.agents.TestCaseExtractionAgent;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.model.GenAiModel;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.utils.UiCommonUtils;
import org.tarik.ta.core.utils.PromptUtils;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.utils.ScreenRecorder;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.AgentConfig;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.UUID;

import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;
import org.tarik.ta.knowledge_graph.service.DecompositionService;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.Neo4jConnectionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;
import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.dto.UiTestExecutionResult;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class UiTestAgentTest {

    private GenAiModel mockModel;
    @Mock
    private ChatModel mockChatModel;
    @Mock
    private BufferedImage mockScreenshot;
    @Mock
    private TestCaseExtractionAgent testCaseExtractionAgentMock;
    @Mock
    private UiPreconditionActionAgent preconditionActionAgentMock;
    @Mock
    private UiPreconditionVerificationAgent preconditionVerificationAgentMock;
    @Mock
    private UiTestStepActionAgent uiTestStepActionAgentMock;
    @Mock
    private UiTestStepVerificationAgent uiTestStepVerificationAgentMock;
    @Mock
    private UiStateCheckAgent uiStateCheckAgentMock;
    @Mock
    private UiElementBoundingBoxAgent uiElementBoundingBoxAgentMock;
    @Mock
    private BestUiElementMatchSelectionAgent bestUiElementMatchSelectionAgentMock;
    @Mock
    private DbUiElementSelectionAgent dbUiElementSelectionAgentMock;
    @Mock
    private UiElementExtendedDescriptionAgent uiElementExtendedDescriptionAgentMock;
    @Mock
    private UiElementResolutionAgent uiElementResolutionAgentMock;
    @Mock
    private KnowledgeSuggestionAgent knowledgeSuggestionAgentMock;

    @Mock
    private AiServices<TestCaseExtractionAgent> testCaseExtractionAgentBuilder;
    @Mock
    private AiServices<UiPreconditionActionAgent> preconditionActionAgentBuilder;
    @Mock
    private AiServices<UiPreconditionVerificationAgent> preconditionVerificationAgentBuilder;
    @Mock
    private AiServices<UiTestStepActionAgent> testStepActionAgentBuilder;
    @Mock
    private AiServices<UiTestStepVerificationAgent> testStepVerificationAgentBuilder;
    @Mock
    private AiServices<UiStateCheckAgent> toolVerificationAgentBuilder;
    @Mock
    private AiServices<UiElementBoundingBoxAgent> elementBoundingBoxAgentBuilder;
    @Mock
    private AiServices<BestUiElementMatchSelectionAgent> elementSelectionAgentBuilder;
    @Mock
    private AiServices<DbUiElementSelectionAgent> dbElementSelectionAgentBuilder;
    @Mock
    private AiServices<UiElementExtendedDescriptionAgent> uiElementExtendedDescriptionAgentBuilder;
    @Mock
    private AiServices<UiElementResolutionAgent> knowledgeCollectionElementResolutionAgentBuilder;
    @Mock
    private AiServices<KnowledgeSuggestionAgent> knowledgeSuggestionAgentBuilder;

    // Static mocks
    private MockedStatic<ModelFactory> modelFactoryMockedStatic;
    private MockedStatic<UiCommonUtils> commonUtilsMockedStatic;
    private MockedStatic<CommonUtils> coreUtilsMockedStatic;
    private MockedStatic<AgentConfig> agentConfigMockedStatic;
    private MockedStatic<UiTestAgentConfig> uiAgentConfigMockedStatic;
    private MockedStatic<AiServices> aiServicesMockedStatic;
    private MockedConstruction<UiElementRepository> uiElementRepositoryMockedConstruction;
    private MockedStatic<PromptUtils> promptUtilsMockedStatic;
    private MockedStatic<Neo4jConnectionManager> neo4jConnectionManagerMockedStatic;
    private MockedConstruction<ScreenRecorder> screenRecorderMockedConstruction;
    private MockedConstruction<KnowledgeService> knowledgeServiceMockedConstruction;
    private MockedConstruction<ProcedureRepository> procedureRepositoryMockedConstruction;
    private MockedConstruction<EmbeddingService> embeddingServiceMockedConstruction;
    private MockedConstruction<DecompositionService> decompositionServiceMockedConstruction;
    private MockedStatic<TestCaseExtractor> testCaseExtractorMockedStatic;

    @Mock
    private org.neo4j.driver.Driver mockNeo4jDriver;
    @Mock
    private org.neo4j.driver.ExecutableQuery mockExecutableQuery;
    @Mock
    private org.neo4j.driver.Session mockNeo4jSession;

    private static final int ACTION_VERIFICATION_DELAY_MILLIS = 5;

    @BeforeEach
    void setUp() {
        modelFactoryMockedStatic = mockStatic(ModelFactory.class);
        commonUtilsMockedStatic = mockStatic(UiCommonUtils.class);
        coreUtilsMockedStatic = mockStatic(CommonUtils.class);
        agentConfigMockedStatic = mockStatic(AgentConfig.class);
        uiAgentConfigMockedStatic = mockStatic(UiTestAgentConfig.class);
        aiServicesMockedStatic = mockStatic(AiServices.class);
        uiElementRepositoryMockedConstruction = mockConstruction(UiElementRepository.class);
        screenRecorderMockedConstruction = mockConstruction(ScreenRecorder.class);
        promptUtilsMockedStatic = mockStatic(PromptUtils.class);
        neo4jConnectionManagerMockedStatic = mockStatic(Neo4jConnectionManager.class);

        neo4jConnectionManagerMockedStatic.when(Neo4jConnectionManager::getDriver).thenReturn(mockNeo4jDriver);
        neo4jConnectionManagerMockedStatic.when(() -> Neo4jConnectionManager.executableQuery(anyString())).thenReturn(mockExecutableQuery);
        neo4jConnectionManagerMockedStatic.when(Neo4jConnectionManager::getSession).thenReturn(mockNeo4jSession);
        lenient().when(mockNeo4jDriver.executableQuery(anyString())).thenReturn(mockExecutableQuery);
        lenient().when(mockExecutableQuery.withParameters(anyMap())).thenReturn(mockExecutableQuery);
        lenient().when(mockExecutableQuery.withConfig(any())).thenReturn(mockExecutableQuery);
        lenient().when(mockExecutableQuery.execute()).thenReturn(mock(org.neo4j.driver.EagerResult.class));

        procedureRepositoryMockedConstruction = mockConstruction(ProcedureRepository.class);
        embeddingServiceMockedConstruction = mockConstruction(EmbeddingService.class);
        decompositionServiceMockedConstruction = mockConstruction(DecompositionService.class);
        testCaseExtractorMockedStatic = mockStatic(TestCaseExtractor.class);
        knowledgeServiceMockedConstruction = mockConstruction(KnowledgeService.class, org.mockito.Mockito.withSettings().defaultAnswer(org.mockito.Answers.RETURNS_MOCKS), (mock, context) -> {
            when(mock.findBestMatch(anyString(), any(), any())).thenAnswer(invocation -> {
                String desc = invocation.getArgument(0);
                if ("UNKNOWN_ACTION".equals(desc)) {
                    return Optional.empty();
                }
                if ("COMPOSITE_ACTION".equals(desc)) {
                    Procedure composite = Procedure.createComposite("COMPOSITE_ACTION",
                            List.of(), "", List.of(), List.of(), false);
                    return Optional.of(new KnowledgeService.MatchResult(composite,
                            KnowledgeService.MatchConfidence.HIGH, List.of(composite)));
                }
                Procedure atomic = Procedure.createAtomic(desc, List.of(), "", List.of(),
                        List.of(), false);
                return Optional.of(new KnowledgeService.MatchResult(atomic,
                        KnowledgeService.MatchConfidence.HIGH, List.of(atomic)));
            });
            lenient().when(mock.resolveToAtomicSteps(any(UUID.class))).thenAnswer(invocation -> {
                Procedure step1 = Procedure.createAtomic("Step 1", List.of(), "", List.of(),
                        List.of(), false);
                Procedure step2 = Procedure.createAtomic("Step 2", List.of(), "", List.of(),
                        List.of(), false);
                return List.of(step1, step2);
            });
        });

        // Agent Config
        agentConfigMockedStatic.when(AgentConfig::getActionVerificationDelayMillis)
                .thenReturn(ACTION_VERIFICATION_DELAY_MILLIS);
        agentConfigMockedStatic.when(AgentConfig::getActionRetryPolicy)
                .thenReturn(new RetryPolicy(3, 100, 5000));
        agentConfigMockedStatic.when(AgentConfig::getVerificationRetryPolicy)
                .thenReturn(new RetryPolicy(3, 100, 5000));
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getNeo4jDatabase).thenReturn("neo4j");
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::isFullyUnattended).thenReturn(true);
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getExecutionMode)
                .thenReturn(ExecutionMode.UNATTENDED);
        agentConfigMockedStatic.when(AgentConfig::getTestCaseExtractionAgentModelProvider)
                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
        agentConfigMockedStatic.when(AgentConfig::getPreconditionActionAgentModelProvider)
                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
        agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentModelProvider)
                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
        agentConfigMockedStatic.when(UiTestAgentConfig::getPreconditionVerificationAgentModelProvider)
                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
        agentConfigMockedStatic.when(UiTestAgentConfig::getTestStepVerificationAgentModelProvider)
                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
        agentConfigMockedStatic.when(AgentConfig::getTestCaseExtractionAgentModelName).thenReturn("test-model");
        agentConfigMockedStatic.when(AgentConfig::getPreconditionActionAgentModelName).thenReturn("test-model");
        agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentModelName).thenReturn("test-model");
        agentConfigMockedStatic.when(UiTestAgentConfig::getPreconditionVerificationAgentModelName)
                .thenReturn("test-model");
        agentConfigMockedStatic.when(UiTestAgentConfig::getTestStepVerificationAgentModelName)
                .thenReturn("test-model");
        agentConfigMockedStatic.when(AgentConfig::getPreconditionAgentPromptVersion).thenReturn("v1");
        agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentPromptVersion).thenReturn("v1");
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getElementBoundingBoxAgentModelName)
                .thenReturn("test-model");
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getUiElementVisualMatchAgentModelName)
                .thenReturn("test-model");
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getElementBoundingBoxAgentModelProvider)
                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getUiElementVisualMatchAgentModelProvider)
                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getElementBoundingBoxAgentPromptVersion)
                .thenReturn("v1");
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getElementSelectionAgentPromptVersion)
                .thenReturn("v1");
        agentConfigMockedStatic.when(UiTestAgentConfig::getPreconditionVerificationAgentPromptVersion)
                .thenReturn("v1");
        agentConfigMockedStatic.when(UiTestAgentConfig::getTestStepVerificationAgentPromptVersion)
                .thenReturn("v1");

        mockModel = new GenAiModel(mockChatModel);

        modelFactoryMockedStatic.when(() -> ModelFactory.getModel(any(), any())).thenReturn(mockModel);
        modelFactoryMockedStatic.when(() -> ModelFactory.getModel(any(), any(), anyInt()))
                .thenReturn(mockModel);

        // Common Utils & Core Utils
        coreUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(anyString())).thenCallRealMethod();
        coreUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(null)).thenReturn(false);
        coreUtilsMockedStatic.when(() -> CommonUtils.isBlank(anyString())).thenCallRealMethod();
        coreUtilsMockedStatic.when(() -> CommonUtils.isBlank(null)).thenReturn(true);
        coreUtilsMockedStatic.when(() -> CommonUtils.getDurationInMillis(any())).thenCallRealMethod();
        commonUtilsMockedStatic.when(UiCommonUtils::captureScreen).thenReturn(mockScreenshot);
        coreUtilsMockedStatic.when(() -> sleepMillis(anyLong())).thenAnswer(invocation -> null);

        promptUtilsMockedStatic.when(() -> loadSystemPrompt(any(), any(), any())).thenReturn("System Prompt");

        // AiServices Mocking
        aiServicesMockedStatic.when(() -> AiServices.builder(TestCaseExtractionAgent.class))
                .thenReturn(testCaseExtractionAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(UiPreconditionActionAgent.class))
                .thenReturn(preconditionActionAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(UiPreconditionVerificationAgent.class))
                .thenReturn(preconditionVerificationAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(UiTestStepActionAgent.class))
                .thenReturn(testStepActionAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(UiTestStepVerificationAgent.class))
                .thenReturn(testStepVerificationAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(UiStateCheckAgent.class))
                .thenReturn(toolVerificationAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(UiElementBoundingBoxAgent.class))
                .thenReturn(elementBoundingBoxAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(BestUiElementMatchSelectionAgent.class))
                .thenReturn(elementSelectionAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(DbUiElementSelectionAgent.class))
                .thenReturn(dbElementSelectionAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(UiElementExtendedDescriptionAgent.class))
                .thenReturn(uiElementExtendedDescriptionAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(UiElementResolutionAgent.class))
                .thenReturn(knowledgeCollectionElementResolutionAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(KnowledgeSuggestionAgent.class))
                .thenReturn(knowledgeSuggestionAgentBuilder);

        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getDbElementCandidateSelectionAgentModelName)
                .thenReturn("test-model");
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getDbElementCandidateSelectionAgentModelProvider)
                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getDbElementCandidateSelectionAgentPromptVersion)
                .thenReturn("v1");

        // Builder chains
        configureBuilder(testCaseExtractionAgentBuilder, testCaseExtractionAgentMock);
        configureBuilder(preconditionActionAgentBuilder, preconditionActionAgentMock);
        configureBuilder(preconditionVerificationAgentBuilder, preconditionVerificationAgentMock);
        configureBuilder(testStepActionAgentBuilder, uiTestStepActionAgentMock);
        configureBuilder(testStepVerificationAgentBuilder, uiTestStepVerificationAgentMock);
        configureBuilder(toolVerificationAgentBuilder, uiStateCheckAgentMock);
        configureBuilder(elementBoundingBoxAgentBuilder, uiElementBoundingBoxAgentMock);
        configureBuilder(elementSelectionAgentBuilder, bestUiElementMatchSelectionAgentMock);
        configureBuilder(dbElementSelectionAgentBuilder, dbUiElementSelectionAgentMock);
        configureBuilder(uiElementExtendedDescriptionAgentBuilder, uiElementExtendedDescriptionAgentMock);

        configureBuilder(knowledgeCollectionElementResolutionAgentBuilder, uiElementResolutionAgentMock);
        configureBuilder(knowledgeSuggestionAgentBuilder, knowledgeSuggestionAgentMock);
    }

    private <T> void configureBuilder(AiServices<T> builder, T agent) {
        lenient().when(builder.chatModel(any())).thenReturn(builder);
        lenient().when(builder.tools(any(Object[].class))).thenReturn(builder);
        lenient().when(builder.toolExecutionErrorHandler(any())).thenReturn(builder);
        lenient().when(builder.systemMessageProvider(any())).thenReturn(builder);
        lenient().when(builder.maxSequentialToolsInvocations(anyInt())).thenReturn(builder);
        lenient().when(builder.toolProvider(any())).thenReturn(builder);
        lenient().when(builder.build()).thenReturn(agent);
    }

    @AfterEach
    void tearDown() {
        modelFactoryMockedStatic.close();
        commonUtilsMockedStatic.close();
        coreUtilsMockedStatic.close();
        agentConfigMockedStatic.close();
        aiServicesMockedStatic.close();
        uiElementRepositoryMockedConstruction.close();
        screenRecorderMockedConstruction.close();
        promptUtilsMockedStatic.close();
        uiAgentConfigMockedStatic.close();
        neo4jConnectionManagerMockedStatic.close();

        procedureRepositoryMockedConstruction.close();
        embeddingServiceMockedConstruction.close();
        decompositionServiceMockedConstruction.close();
        knowledgeServiceMockedConstruction.close();
        testCaseExtractorMockedStatic.close();
    }

    @Test
    @DisplayName("Single test step with action and successful verification")
    void singleStepActionAndVerificationSuccess() {
        // Given
        TestStep step = new TestStep("Perform Action", null, "Verify Result");
        TestCase testCase = new TestCase("Single Step Success", null, List.of(step));

        mockTestCaseExtraction(testCase);

        doReturn(new UiOperationExecutionResult<>(SUCCESS, "Action executed", new EmptyExecutionResult(),
                mockScreenshot))
                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

        doReturn(new UiOperationExecutionResult<>(SUCCESS, "Verification executed",
                new VerificationExecutionResult(true, "Verified"), mockScreenshot))
                .when(uiTestStepVerificationAgentMock).executeAndGetResult(any(Supplier.class));

        // When
        TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getStepResults()).hasSize(1);
        assertThat(result.getStepResults().getFirst().getExecutionStatus())
                .isEqualTo(TestStepResultStatus.SUCCESS);

        verify(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));
        verify(uiTestStepVerificationAgentMock).executeAndGetResult(any(Supplier.class));
    }

    @Test
    @DisplayName("Single step with action only (no verification)")
    void singleStepActionOnlySuccess() {
        // Given
        TestStep step = new TestStep("Perform Action Only", null, null);
        TestCase testCase = new TestCase("Single Action Only", null, List.of(step));

        mockTestCaseExtraction(testCase);

        doReturn(new UiOperationExecutionResult<>(SUCCESS, "Action executed", new EmptyExecutionResult(),
                mockScreenshot))
                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

        // When
        TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        verify(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));
        verifyNoInteractions(uiTestStepVerificationAgentMock);
    }

    @Test
    @DisplayName("Preconditions execution and verification success")
    void preconditionsSuccess() {
        // Given
        String precondition = "Precondition 1";
        TestStep step = new TestStep("Action", null, null);
        TestCase testCase = new TestCase("Precondition Success", List.of(precondition), List.of(step));

        mockTestCaseExtraction(testCase);

        doReturn(new UiOperationExecutionResult<>(SUCCESS, "Precondition executed",
                new EmptyExecutionResult(), mockScreenshot))
                .when(preconditionActionAgentMock).executeAndGetResult(any(Supplier.class));

        doReturn(new UiOperationExecutionResult<>(SUCCESS, "Precondition verified",
                new VerificationExecutionResult(true, "Verified"), mockScreenshot))
                .when(preconditionVerificationAgentMock).executeAndGetResult(any(Supplier.class));

        doReturn(new UiOperationExecutionResult<>(SUCCESS, "Action executed", new EmptyExecutionResult(),
                mockScreenshot))
                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

        // When
        TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        verify(preconditionActionAgentMock).executeAndGetResult(any(Supplier.class));
        verify(preconditionVerificationAgentMock).executeAndGetResult(any(Supplier.class));
        verify(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));
    }

    @Test
    @DisplayName("Precondition execution fails")
    void preconditionExecutionFails() {
        // Given
        String precondition = "Precondition 1";
        TestCase testCase = new TestCase("Precondition Fail", List.of(precondition),
                List.of(new TestStep("Dummy Step", null, null)));

        mockTestCaseExtraction(testCase);

        doReturn(new UiOperationExecutionResult<>(ERROR, "Precondition failed", null, mockScreenshot))
                .when(preconditionActionAgentMock).executeAndGetResult(any(Supplier.class));

        // When
        TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
        verify(preconditionActionAgentMock).executeAndGetResult(any(Supplier.class));
        verifyNoInteractions(preconditionVerificationAgentMock);
        verifyNoInteractions(uiTestStepActionAgentMock);
    }

    @Test
    @DisplayName("Execution result contains SystemInfo and logs")
    void executionResultContainsSystemInfoAndLogs() {
        // Given
        TestStep step = new TestStep("Action", null, null);
        TestCase testCase = new TestCase("System Info Test", null, List.of(step));

        mockTestCaseExtraction(testCase);

        doReturn(new UiOperationExecutionResult<>(SUCCESS, "Action executed", new EmptyExecutionResult(),
                mockScreenshot))
                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

        // When
        UiTestExecutionResult result = (UiTestExecutionResult) UiTestAgent
                .executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getSystemInfo()).isNotNull();
        assertThat(result.getSystemInfo().device()).isNotNull();
        assertThat(result.getSystemInfo().osVersion()).isNotBlank();
        assertThat(result.getLogs()).isNotNull();
        assertThat(result.getLogs()).isNotEmpty(); // Should contain at least start/end logs
    }

    @Test
    @DisplayName("Unattended mode returns error when procedure is missing")
    void missingProcedureInUnattendedModeReturnsError() {
        // Given
        TestStep step = new TestStep("UNKNOWN_ACTION", null, null);
        TestCase testCase = new TestCase("Missing Knowledge Test", null, List.of(step));

        mockTestCaseExtraction(testCase);

        // When
        TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
        assertThat(result.getGeneralErrorMessage()).contains("Knowledge-based execution error");
        // The actual error message comes from the MissingProcedureException wrapped
        // in "Knowledge-based execution error: ..."
        // The exception message is "No matching procedure found for
        // 'UNKNOWN_ACTION' and HITL is not available in UNATTENDED mode"
        assertThat(result.getGeneralErrorMessage()).contains("No matching procedure found");
    }

    @Test
    @DisplayName("Composite procedure aggregation success")
    void compositeProcedureAggregationSuccess() {
        // Given
        TestStep step = new TestStep("COMPOSITE_ACTION", null, null);
        TestCase testCase = new TestCase("Composite Test", null, List.of(step));

        mockTestCaseExtraction(testCase);

        // Mock action execution for 2 atomic steps (Step 1 and Step 2 as defined in the
        // mock setup)
        // We need to return SUCCESS for both
        doReturn(new UiOperationExecutionResult<>(SUCCESS, "Step 1 executed", new EmptyExecutionResult(),
                mockScreenshot))
                .doReturn(new UiOperationExecutionResult<>(SUCCESS, "Step 2 executed",
                        new EmptyExecutionResult(), mockScreenshot))
                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

        // When
        TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getStepResults()).hasSize(1);
        // The result should be aggregated into a single success result
        assertThat(result.getStepResults().getFirst().getExecutionStatus())
                .isEqualTo(TestStepResultStatus.SUCCESS);
        // The step description should match the original step
        assertThat(result.getStepResults().getFirst().getTestStep().stepDescription())
                .isEqualTo("COMPOSITE_ACTION");

        // Verify that action agent was called twice (once for each atomic step)
        verify(uiTestStepActionAgentMock, times(2)).executeAndGetResult(any(Supplier.class));
    }

    private void mockTestCaseExtraction(TestCase testCase) {
        testCaseExtractorMockedStatic
                .when(() -> TestCaseExtractor.extractTestCase(anyString()))
                .thenReturn(Optional.of(testCase));
    }
}
