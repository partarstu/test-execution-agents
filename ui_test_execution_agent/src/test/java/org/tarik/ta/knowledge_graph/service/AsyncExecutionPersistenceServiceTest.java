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
package org.tarik.ta.knowledge_graph.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncExecutionPersistenceServiceTest {

    private AsyncExecutionPersistenceService service;
    private ListAppender<ILoggingEvent> logAppender;

    @Mock private UiTestAgentConfig mockConfig;
    @Mock private SatisfiesEdgeService mockSatisfiesEdgeService;
    @Mock private KnowledgeService mockKnowledgeService;
    @Mock private ProcedureRepository mockProcedureRepository;
    @Mock private FailureContextService mockFailureContextService;

    @BeforeEach
    void setUp() {
        service = new AsyncExecutionPersistenceService(mockConfig, mockSatisfiesEdgeService,
                mockKnowledgeService, mockProcedureRepository, mockFailureContextService);
    }

    @AfterEach
    void tearDown() {
        if (logAppender != null) {
            ((Logger) LoggerFactory.getLogger(AsyncExecutionPersistenceService.class)).detachAppender(logAppender);
            logAppender.stop();
        }
    }

    private ListAppender<ILoggingEvent> captureAsyncPersistenceLogs() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(AsyncExecutionPersistenceService.class)).addAppender(logAppender);
        return logAppender;
    }

    @Test
    @DisplayName("persistSatisfiesEdges runs synchronously in SUPERVISED mode")
    void persistSatisfiesEdges_syncInSupervised() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        UUID id = UUID.randomUUID();

        service.persistSatisfiesEdges(id);

        verify(mockSatisfiesEdgeService).persistSatisfiesEdges(id);
    }

    @Test
    @DisplayName("persistSatisfiesEdges runs asynchronously in UNATTENDED mode")
    void persistSatisfiesEdges_asyncInUnattended() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mockSatisfiesEdgeService).persistSatisfiesEdges(id);

        service.persistSatisfiesEdges(id);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mockSatisfiesEdgeService).persistSatisfiesEdges(id);
    }

    @Test
    @DisplayName("updateTimingProfile runs synchronously in SUPERVISED mode")
    void updateTimingProfile_syncInSupervised() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        UUID id = UUID.randomUUID();

        service.updateTimingProfile(id, 100, 200);

        verify(mockKnowledgeService).updateTimingProfile(id, 100, 200);
    }

    @Test
    @DisplayName("updateTimingProfile runs asynchronously in UNATTENDED mode")
    void updateTimingProfile_asyncInUnattended() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mockKnowledgeService).updateTimingProfile(id, 100, 200);

        service.updateTimingProfile(id, 100, 200);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mockKnowledgeService).updateTimingProfile(id, 100, 200);
    }

    @Test
    @DisplayName("updateElementStability runs synchronously in SUPERVISED mode")
    void updateElementStability_syncInSupervised() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        UUID id = UUID.randomUUID();

        service.updateElementStability(id, true, 100);

        verify(mockProcedureRepository).updateElementStability(id, true, 100);
    }

    @Test
    @DisplayName("updateElementStability runs asynchronously in UNATTENDED mode")
    void updateElementStability_asyncInUnattended() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mockProcedureRepository).updateElementStability(id, true, 100);

        service.updateElementStability(id, true, 100);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mockProcedureRepository).updateElementStability(id, true, 100);
    }

    @Test
    @DisplayName("captureFailureContext runs synchronously in SUPERVISED mode")
    void captureFailureContext_syncInSupervised() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        UUID id = UUID.randomUUID();
        FailureContext fc = mock(FailureContext.class);

        service.captureFailureContext(id, fc);

        verify(mockFailureContextService).captureFailureContext(id, fc);
    }

    @Test
    @DisplayName("captureFailureContext runs asynchronously in UNATTENDED mode")
    void captureFailureContext_asyncInUnattended() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        FailureContext fc = mock(FailureContext.class);
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mockFailureContextService).captureFailureContext(id, fc);

        service.captureFailureContext(id, fc);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mockFailureContextService).captureFailureContext(id, fc);
    }

    @Test
    @DisplayName("persistSatisfiesEdges logs ERROR on async failure in UNATTENDED mode")
    void persistSatisfiesEdges_asyncLogsErrorOnFailure() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        var appender = captureAsyncPersistenceLogs();

        doAnswer(inv -> { latch.countDown(); throw new RuntimeException("persistence failure"); })
                .when(mockSatisfiesEdgeService).persistSatisfiesEdges(id);

        service.persistSatisfiesEdges(id);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);
        assertThat(appender.list).anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("persistSatisfiesEdges"));
    }

    @Test
    @DisplayName("updateTimingProfile logs ERROR on async failure in UNATTENDED mode")
    void updateTimingProfile_asyncLogsErrorOnFailure() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        var appender = captureAsyncPersistenceLogs();

        doAnswer(inv -> { latch.countDown(); throw new RuntimeException("persistence failure"); })
                .when(mockKnowledgeService).updateTimingProfile(id, 100, 200);

        service.updateTimingProfile(id, 100, 200);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);
        assertThat(appender.list).anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("updateTimingProfile"));
    }

    @Test
    @DisplayName("updateElementStability logs ERROR on async failure in UNATTENDED mode")
    void updateElementStability_asyncLogsErrorOnFailure() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        var appender = captureAsyncPersistenceLogs();

        doAnswer(inv -> { latch.countDown(); throw new RuntimeException("persistence failure"); })
                .when(mockProcedureRepository).updateElementStability(id, true, 100);

        service.updateElementStability(id, true, 100);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);
        assertThat(appender.list).anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("updateElementStability"));
    }

    @Test
    @DisplayName("captureFailureContext logs ERROR on async failure in UNATTENDED mode")
    void captureFailureContext_asyncLogsErrorOnFailure() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        FailureContext fc = mock(FailureContext.class);
        CountDownLatch latch = new CountDownLatch(1);
        var appender = captureAsyncPersistenceLogs();

        doAnswer(inv -> { latch.countDown(); throw new RuntimeException("persistence failure"); })
                .when(mockFailureContextService).captureFailureContext(id, fc);

        service.captureFailureContext(id, fc);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);
        assertThat(appender.list).anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("captureFailureContext"));
    }
}
