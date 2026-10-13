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
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly.Kind;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.model.node.Procedure.TimingProfile;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock private ProcedureUsageByTestCaseTrackingService mockProcedureUsageByTestCaseTrackingService;
    @Mock private ExecutionAnomalyService mockExecutionAnomalyService;

    @BeforeEach
    void setUp() {
        service = new AsyncExecutionPersistenceService(mockConfig, mockSatisfiesEdgeService,
                mockKnowledgeService, mockProcedureRepository, mockFailureContextService,
                mockProcedureUsageByTestCaseTrackingService, mockExecutionAnomalyService);
    }

    @AfterEach
    void tearDown() {
        service.close();
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
            return Optional.empty();
        }).when(mockKnowledgeService).updateTimingProfile(id, 100, 200);

        service.updateTimingProfile(id, 100, 200);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mockKnowledgeService).updateTimingProfile(id, 100, 200);
    }

    @Test
    @DisplayName("updateElementStability runs synchronously in SUPERVISED mode")
    void updateElementStability_syncInSupervised() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        when(mockConfig.isLocationHistoryAndFailureHintsCollectionEnabled()).thenReturn(true);
        UUID id = UUID.randomUUID();

        service.updateElementStability(id, true, 100);

        verify(mockProcedureRepository).updateElementStability(id, true, 100);
    }

    @Test
    @DisplayName("updateElementStability runs asynchronously in UNATTENDED mode")
    void updateElementStability_asyncInUnattended() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        when(mockConfig.isLocationHistoryAndFailureHintsCollectionEnabled()).thenReturn(true);
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
    void updateElementStability_shouldRecordTheFailedLocationButSkipTheStabilityWrite_whenCollectionIsDisabled() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        when(mockConfig.isLocationHistoryAndFailureHintsCollectionEnabled()).thenReturn(false);
        UUID id = UUID.randomUUID();

        service.updateElementStability(id, false, 1500);

        verify(mockExecutionAnomalyService).record(Kind.ELEMENT_LOCATION_FAILED, id, "Element not located after 1500 ms");
        verifyNoInteractions(mockProcedureRepository);
    }

    @Test
    void updateElementStability_shouldNotRecordAnAnomaly_whenTheElementWasLocated() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        when(mockConfig.isLocationHistoryAndFailureHintsCollectionEnabled()).thenReturn(false);

        service.updateElementStability(UUID.randomUUID(), true, 100);

        verifyNoInteractions(mockExecutionAnomalyService);
    }

    @Test
    void updateTimingProfile_shouldRecordATimingOutlier_whenTheExecutionTookFarLongerThanTheAverage() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        when(mockConfig.getAnomalyTimingOutlierFactor()).thenReturn(3.0);
        UUID id = UUID.randomUUID();
        when(mockKnowledgeService.updateTimingProfile(id, 3100, 0)).thenReturn(Optional.of(new TimingProfile(1000, 0, 0, Instant.now())));

        service.updateTimingProfile(id, 3100, 0);

        verify(mockExecutionAnomalyService).record(Kind.TIMING_OUTLIER, id,
                "Execution took 3100 ms, more than %.1f times the average of 1000 ms".formatted(3.0));
    }

    @Test
    void updateTimingProfile_shouldNotRecordATimingOutlier_whenTheExecutionStaysWithinTheFactor() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        when(mockConfig.getAnomalyTimingOutlierFactor()).thenReturn(3.0);
        UUID id = UUID.randomUUID();
        when(mockKnowledgeService.updateTimingProfile(id, 3000, 0)).thenReturn(Optional.of(new TimingProfile(1000, 0, 0, Instant.now())));

        service.updateTimingProfile(id, 3000, 0);

        verifyNoInteractions(mockExecutionAnomalyService);
    }

    @Test
    void recordAnomaly_shouldRecordSynchronously_inSupervisedMode() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        UUID id = UUID.randomUUID();

        service.recordAnomaly(Kind.STEP_FAILED, id, "verification failed");

        verify(mockExecutionAnomalyService).record(Kind.STEP_FAILED, id, "verification failed");
    }

    @Test
    @DisplayName("recordAnomaly runs asynchronously in UNATTENDED mode")
    void recordAnomaly_shouldRecordAsynchronously_inUnattendedMode() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        var callerThread = Thread.currentThread();
        var recordingThread = new AtomicReference<Thread>();
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            recordingThread.set(Thread.currentThread());
            latch.countDown();
            return null;
        }).when(mockExecutionAnomalyService).record(Kind.STEP_FAILED, id, "verification failed");

        service.recordAnomaly(Kind.STEP_FAILED, id, "verification failed");

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(recordingThread.get()).isNotSameAs(callerThread);
    }

    @Test
    @DisplayName("close lets a write still in flight finish before it returns")
    void close_shouldWaitForAPendingWrite() throws Exception {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        var writeStarted = new CountDownLatch(1);
        var releaseWrite = new CountDownLatch(1);
        var writeFinished = new AtomicBoolean();
        doAnswer(inv -> {
            writeStarted.countDown();
            releaseWrite.await(10, TimeUnit.SECONDS);
            writeFinished.set(true);
            return null;
        }).when(mockSatisfiesEdgeService).persistSatisfiesEdges(id);

        service.persistSatisfiesEdges(id);
        assertThat(writeStarted.await(10, TimeUnit.SECONDS)).isTrue();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var closing = executor.submit(() -> {
                service.close();
                return writeFinished.get();
            });

            assertThatThrownBy(() -> closing.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseWrite.countDown();
            assertThat(closing.get(10, TimeUnit.SECONDS)).as("write finished before close returned").isTrue();
        }
    }

    @Test
    void bindTestCase_shouldBindTheTestCaseOfTheAnomalyService() {
        service.bindTestCase("Login test");

        verify(mockExecutionAnomalyService).bindTestCase("Login test");
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
        when(mockConfig.isLocationHistoryAndFailureHintsCollectionEnabled()).thenReturn(true);
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

    @Test
    @DisplayName("mergeUsesProcedure runs asynchronously in UNATTENDED mode")
    void mergeUsesProcedure_asyncInUnattended() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mockProcedureUsageByTestCaseTrackingService).mergeUsesProcedure("test case", id);

        service.mergeUsesProcedure("test case", id);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mockProcedureUsageByTestCaseTrackingService).mergeUsesProcedure("test case", id);
    }

    @Test
    @DisplayName("mergeUsesProcedure runs synchronously in SUPERVISED mode")
    void mergeUsesProcedure_syncInSupervised() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        UUID id = UUID.randomUUID();

        service.mergeUsesProcedure("test case", id);

        verify(mockProcedureUsageByTestCaseTrackingService).mergeUsesProcedure("test case", id);
    }

    @Test
    @DisplayName("mergeUsesProcedure logs ERROR on async failure in UNATTENDED mode")
    void mergeUsesProcedure_asyncLogsErrorOnFailure() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        var appender = captureAsyncPersistenceLogs();

        doThrow(new RuntimeException("persistence failure"))
                .when(mockProcedureUsageByTestCaseTrackingService).mergeUsesProcedure("test case", id);

        service.mergeUsesProcedure("test case", id);
        service.awaitPendingUsageWrites();

        assertThat(appender.list).anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("mergeUsesProcedure"));
    }

    @Test
    @DisplayName("awaitPendingUsageWrites waits for an in-flight async merge to complete")
    void awaitPendingUsageWrites_waitsForInFlightMerge() throws Exception {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        var mergeStarted = new CountDownLatch(1);
        var releaseMerge = new CountDownLatch(1);
        var mergeFinished = new AtomicBoolean();
        doAnswer(inv -> {
            mergeStarted.countDown();
            releaseMerge.await(10, TimeUnit.SECONDS);
            mergeFinished.set(true);
            return null;
        }).when(mockProcedureUsageByTestCaseTrackingService).mergeUsesProcedure("test case", id);

        service.mergeUsesProcedure("test case", id);
        assertThat(mergeStarted.await(10, TimeUnit.SECONDS)).isTrue();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var awaiting = executor.submit(() -> {
                service.awaitPendingUsageWrites();
                return mergeFinished.get();
            });

            assertThatThrownBy(() -> awaiting.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseMerge.countDown();
            assertThat(awaiting.get(10, TimeUnit.SECONDS)).as("merge finished before the wait returned").isTrue();
        }
    }

    @Test
    @DisplayName("awaitPendingUsageWrites is a no-op when nothing is in flight")
    void awaitPendingUsageWrites_noOpWhenNothingPending() {
        service.awaitPendingUsageWrites();

        verifyNoInteractions(mockProcedureUsageByTestCaseTrackingService);
    }

    @Test
    @DisplayName("persistRunHistory runs synchronously in SUPERVISED mode")
    void persistRunHistory_shouldWriteAtOnce_inSupervisedMode() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        var written = new AtomicBoolean();

        service.persistRunHistory(() -> written.set(true), "write");

        assertThat(written).isTrue();
    }

    @Test
    @DisplayName("persistRunHistory runs off the caller's thread in UNATTENDED mode, each write after the previous one")
    void persistRunHistory_shouldWriteInOrderOffTheCallersThread_inUnattendedMode() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        var releaseFirst = new CountDownLatch(1);
        var order = new ConcurrentLinkedQueue<String>();
        var caller = Thread.currentThread();
        var threads = new ConcurrentLinkedQueue<Thread>();

        service.persistRunHistory(() -> {
            threads.add(Thread.currentThread());
            awaitQuietly(releaseFirst);
            order.add("run");
        }, "createRun");
        service.persistRunHistory(() -> order.add("resolution"), "appendResolution");
        releaseFirst.countDown();
        service.awaitPendingUsageWrites();

        assertThat(order).containsExactly("run", "resolution");
        assertThat(threads).doesNotContain(caller);
    }

    @Test
    @DisplayName("awaitRunHistoryWrites returns only after every queued run-history write has landed")
    void awaitRunHistoryWrites_shouldWaitForTheQueuedWrites() throws Exception {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        var releaseRun = new CountDownLatch(1);
        var order = new ConcurrentLinkedQueue<String>();
        service.persistRunHistory(() -> {
            awaitQuietly(releaseRun);
            order.add("run");
        }, "createRun");
        service.persistRunHistory(() -> order.add("resolution"), "appendResolution");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var awaiting = executor.submit(() -> {
                service.awaitRunHistoryWrites();
                return List.copyOf(order);
            });

            assertThatThrownBy(() -> awaiting.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseRun.countDown();
            assertThat(awaiting.get(10, TimeUnit.SECONDS)).containsExactly("run", "resolution");
        }
    }

    @Test
    @DisplayName("A failing run-history write is logged and does not stop the next one")
    void persistRunHistory_shouldLogAFailedWrite_andStillRunTheNextOne() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        var logs = captureAsyncPersistenceLogs();
        var secondWritten = new AtomicBoolean();

        service.persistRunHistory(() -> {
            throw new IllegalStateException("graph down");
        }, "createRun");
        service.persistRunHistory(() -> secondWritten.set(true), "appendResolution");
        service.awaitPendingUsageWrites();

        assertThat(secondWritten).isTrue();
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("createRun");
        });
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
