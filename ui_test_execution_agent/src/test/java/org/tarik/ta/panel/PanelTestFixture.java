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
import org.jetbrains.annotations.Nullable;
import org.mockito.quality.Strictness;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.dto.panel.SessionInfo;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.GraphHealthService;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeViewService;
import org.tarik.ta.knowledge_graph.service.ProcedureUsageByTestCaseTrackingService;
import org.tarik.ta.panel.ProcedureEditorPrompt.EditorServices;
import org.tarik.ta.tools.UiElementRefinementHelper;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** A running operator panel on a random local port with a controllable clock, for the panel tests and the smoke suite. */
public final class PanelTestFixture implements AutoCloseable {
    public static final String PASSWORD = "correct-horse-battery-staple";
    static final int SEARCH_DELAY_MILLIS = 300;
    private static final int ACTION_WAIT_MILLIS = 5000;
    static final String STATIC_FILES_DIRECTORY = "/panel-test";

    private final MutableClock clock;
    private final PanelAuth auth;
    private final ExecutionStateBroadcaster broadcaster;
    private final VncRelay relay;
    private final WebOperatorPanel operatorPanel;
    private final PanelServer server;
    private final String baseUrl;

    private PanelTestFixture(Builder builder) {
        this.clock = new MutableClock();
        boolean tls = builder.tlsCertificate != null;
        var config = mock(UiTestAgentConfig.class, withSettings().strictness(Strictness.LENIENT));
        when(config.getPanelHost()).thenReturn("127.0.0.1");
        when(config.getPanelPort()).thenReturn(0);
        when(config.getPanelPassword()).thenReturn(Optional.of(PASSWORD));
        when(config.getPanelTlsCert()).thenReturn(Optional.ofNullable(builder.tlsCertificate));
        when(config.getPanelTlsKey()).thenReturn(Optional.ofNullable(builder.tlsPrivateKey));
        when(config.isPanelTlsConfigured()).thenReturn(tls);
        when(config.getExecutionMode()).thenReturn(builder.executionMode);
        when(config.getSupervisedCountdownSeconds()).thenReturn(builder.countdownSeconds);
        when(config.getFailureContextDialogCountdownSeconds()).thenReturn(builder.countdownSeconds);
        when(config.getElementResolutionMaxDurationMillis()).thenReturn(ACTION_WAIT_MILLIS);
        var sessionInfo = new SessionInfo(builder.executionMode, builder.vncSocketPath != null, false, SEARCH_DELAY_MILLIS);
        this.auth = new PanelAuth(PASSWORD, builder.idleTimeout, builder.absoluteTimeout, clock, tls, sessionInfo);
        this.broadcaster = new ExecutionStateBroadcaster(auth);
        this.relay = new VncRelay(builder.vncSocketPath, auth, builder.webSocketIdleTimeout);
        this.operatorPanel = new WebOperatorPanel(broadcaster, config, builder.editorServices, InstantSource.system(),
                builder.promptMaxWait);
        this.server = new PanelServer(config, auth, broadcaster, relay, new GraphHealthRoute(builder.healthService),
                new PromptRoutes(operatorPanel), new KnowledgeRoutes(builder.knowledgeViewService), builder.staticFilesDirectory,
                builder.keepAliveInterval, builder.webSocketIdleTimeout);
        server.start();
        this.baseUrl = "%s://127.0.0.1:%d".formatted(tls ? "https" : "http", server.port());
    }

    public static Builder builder() {
        return new Builder();
    }

    public @NotNull String baseUrl() {
        return baseUrl;
    }

    public @NotNull RunObserver runObserver() {
        return operatorPanel;
    }

    /** The prompts the panel asks, answered through its HTTP API. */
    public @NotNull OperatorPrompts operatorPrompts() {
        return operatorPanel;
    }

    WebOperatorPanel operatorPanel() {
        return operatorPanel;
    }

    public void advanceClock(@NotNull Duration duration) {
        clock.advance(duration);
    }

    /** Runs the action on every read of the panel's clock, as if it happened concurrently with whatever reads the time. */
    void onClockRead(@NotNull Runnable action) {
        clock.onRead.set(action);
    }

    void touch(@NotNull String sessionId) {
        auth.touch(sessionId);
    }

    /** Runs one keep-alive tick now instead of waiting for the scheduler. */
    public void keepAliveTick() {
        broadcaster.keepAlive();
        relay.keepAlive();
    }

    PanelServer server() {
        return server;
    }

    ExecutionStateBroadcaster broadcaster() {
        return broadcaster;
    }

    VncRelay relay() {
        return relay;
    }

    @Override
    public void close() {
        server.close();
        relay.close();
        broadcaster.close();
    }

    public static final class Builder {
        private Duration idleTimeout = Duration.ofMinutes(30);
        private Duration absoluteTimeout = Duration.ofHours(8);
        // Long enough that only explicit ticks run, unless a test asks for a real interval
        private Duration keepAliveInterval = Duration.ofHours(1);
        private Duration webSocketIdleTimeout = Duration.ofMinutes(5);
        private @Nullable String vncSocketPath;
        private @Nullable String tlsCertificate;
        private @Nullable String tlsPrivateKey;
        private GraphHealthService healthService = mock(GraphHealthService.class);
        private KnowledgeViewService knowledgeViewService = mock(KnowledgeViewService.class);
        private EditorServices editorServices = new EditorServices(mock(KnowledgeIngestionService.class), mock(UiElementRepository.class),
                mock(ProcedureUsageByTestCaseTrackingService.class), mock(UiElementRefinementHelper.class));
        private int countdownSeconds = 30;
        private Duration promptMaxWait = Duration.ofMinutes(5);
        private String staticFilesDirectory = STATIC_FILES_DIRECTORY;
        private ExecutionMode executionMode = ExecutionMode.SUPERVISED;

        public Builder sessionTimeouts(@NotNull Duration idle, @NotNull Duration absolute) {
            this.idleTimeout = idle;
            this.absoluteTimeout = absolute;
            return this;
        }

        public Builder keepAliveInterval(@NotNull Duration interval) {
            this.keepAliveInterval = interval;
            return this;
        }

        public Builder webSocketIdleTimeout(@NotNull Duration timeout) {
            this.webSocketIdleTimeout = timeout;
            return this;
        }

        public Builder vncSocketPath(@NotNull String socketPath) {
            this.vncSocketPath = socketPath;
            return this;
        }

        public Builder tls(@NotNull String certificate, @NotNull String privateKey) {
            this.tlsCertificate = certificate;
            this.tlsPrivateKey = privateKey;
            return this;
        }

        public Builder editorServices(@NotNull KnowledgeIngestionService ingestionService, @NotNull UiElementRepository repository,
                                      @NotNull ProcedureUsageByTestCaseTrackingService usageTrackingService,
                                      @NotNull UiElementRefinementHelper refinementHelper) {
            this.editorServices = new EditorServices(ingestionService, repository, usageTrackingService, refinementHelper);
            return this;
        }

        /** The countdown of the confirmation and failure-context prompts. */
        public Builder countdownSeconds(int seconds) {
            this.countdownSeconds = seconds;
            return this;
        }

        public Builder promptMaxWait(@NotNull Duration maxWait) {
            this.promptMaxWait = maxWait;
            return this;
        }

        public Builder healthService(@NotNull GraphHealthService service) {
            this.healthService = service;
            return this;
        }

        public Builder knowledgeViewService(@NotNull KnowledgeViewService service) {
            this.knowledgeViewService = service;
            return this;
        }

        /** Serves the frontend from this classpath directory instead of the small test page, e.g. the real bundle at /panel. */
        public Builder staticFilesDirectory(@NotNull String directory) {
            this.staticFilesDirectory = directory;
            return this;
        }

        public Builder executionMode(@NotNull ExecutionMode mode) {
            this.executionMode = mode;
            return this;
        }

        public PanelTestFixture start() {
            return new PanelTestFixture(this);
        }
    }

    /** A clock the test moves by hand, which can also run an action on every read to interleave work with a session lookup. */
    private static final class MutableClock implements InstantSource {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-02T10:00:00Z"));
        private final AtomicReference<Runnable> onRead = new AtomicReference<>(() -> {
        });
        // The action may read the clock itself, which must not run it again
        private final ThreadLocal<Boolean> runningAction = ThreadLocal.withInitial(() -> false);

        @Override
        public Instant instant() {
            if (!runningAction.get()) {
                runningAction.set(true);
                try {
                    onRead.get().run();
                } finally {
                    runningAction.set(false);
                }
            }
            return now.get();
        }

        private void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }
    }
}
