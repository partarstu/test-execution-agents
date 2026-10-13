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

import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.sse.SseClient;
import io.javalin.json.JavalinJackson;
import io.javalin.plugin.bundled.GlobalHeadersConfig;
import io.javalin.plugin.bundled.GlobalHeadersConfig.CrossOriginOpenerPolicy;
import io.javalin.plugin.bundled.GlobalHeadersConfig.CrossOriginResourcePolicy;
import io.javalin.plugin.bundled.GlobalHeadersConfig.ReferrerPolicy;
import io.javalin.plugin.bundled.GlobalHeadersConfig.XFrameOptions;
import io.javalin.plugin.bundled.RateLimitPlugin;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.JavalinAppFactory;
import org.tarik.ta.dto.panel.PanelEvent.Displaced.Reason;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import static io.javalin.http.Header.CACHE_CONTROL;
import static io.javalin.http.staticfiles.Location.CLASSPATH;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.tarik.ta.panel.PanelAuth.LOGIN_PATH;
import static org.tarik.ta.panel.PanelAuth.SESSION_PATH;

/** The operator panel's own server: static frontend, login, event stream, prompts, live view relay, health report and knowledge views. */
@Singleton
public final class PanelServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(PanelServer.class);
    static final int MIN_PASSWORD_LENGTH = 16;
    // Below Jetty's default connection idle timeout of 30 seconds, which would otherwise close a quiet event stream
    static final Duration KEEP_ALIVE_INTERVAL = Duration.ofSeconds(15);
    private static final String STATIC_FILES_DIRECTORY = "/panel";
    private static final String ASSETS_PATH = "/assets";
    private static final long MAX_REQUEST_SIZE_BYTES = 1_000_000;
    // The browser sends only input events through the live view
    private static final int MAX_INBOUND_WEB_SOCKET_BYTES = 64 * 1024;
    private static final int PRECOMPRESS_ALL_SIZES = 0;
    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' blob: data:; object-src 'none'; "
            + "base-uri 'none'; form-action 'self'; frame-ancestors 'none'; require-trusted-types-for 'script'";
    private static final String PERMISSIONS_POLICY_HEADER = "Permissions-Policy";
    private static final String PERMISSIONS_POLICY = "geolocation=(), camera=(), microphone=()";
    private static final Duration STRICT_TRANSPORT_SECURITY_MAX_AGE = Duration.ofDays(365);
    private static final String IMMUTABLE = "public, max-age=31536000, immutable";
    private static final String NO_CACHE = "no-cache";
    private static final String NO_STORE = "no-store";

    private final UiTestAgentConfig config;
    private final PanelAuth auth;
    private final ExecutionStateBroadcaster broadcaster;
    private final VncRelay relay;
    private final GraphHealthRoute graphHealthRoute;
    private final PromptRoutes promptRoutes;
    private final KnowledgeRoutes knowledgeRoutes;
    private final String staticFilesDirectory;
    private final Duration keepAliveInterval;
    private final Duration webSocketIdleTimeout;
    private final ScheduledExecutorService keepAliveScheduler = newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("panel-keep-alive").daemon().factory());
    private @Nullable Javalin app;

    @Inject
    PanelServer(UiTestAgentConfig config, PanelAuth auth, ExecutionStateBroadcaster broadcaster, VncRelay relay,
                GraphHealthRoute graphHealthRoute, PromptRoutes promptRoutes, KnowledgeRoutes knowledgeRoutes) {
        this(config, auth, broadcaster, relay, graphHealthRoute, promptRoutes, knowledgeRoutes, STATIC_FILES_DIRECTORY,
                KEEP_ALIVE_INTERVAL, VncRelay.WEB_SOCKET_IDLE_TIMEOUT);
    }

    PanelServer(@NotNull UiTestAgentConfig config, @NotNull PanelAuth auth, @NotNull ExecutionStateBroadcaster broadcaster,
                @NotNull VncRelay relay, @NotNull GraphHealthRoute graphHealthRoute, @NotNull PromptRoutes promptRoutes,
                @NotNull KnowledgeRoutes knowledgeRoutes,
                @NotNull String staticFilesDirectory, @NotNull Duration keepAliveInterval, @NotNull Duration webSocketIdleTimeout) {
        this.config = config;
        this.auth = auth;
        this.broadcaster = broadcaster;
        this.relay = relay;
        this.graphHealthRoute = graphHealthRoute;
        this.promptRoutes = promptRoutes;
        this.knowledgeRoutes = knowledgeRoutes;
        this.staticFilesDirectory = staticFilesDirectory;
        this.keepAliveInterval = keepAliveInterval;
        this.webSocketIdleTimeout = webSocketIdleTimeout;
        auth.addSessionEndListener(broadcaster::closeSession);
        auth.addSessionEndListener((sessionId, _) -> relay.closeSession(sessionId));
    }

    /**
     * Starts the panel unless no password is configured for an unattended agent on a loopback host; refuses a short password, none in
     * supervised mode or on any other host, and a supervised start without the frontend.
     */
    public void start() {
        var host = config.getPanelHost();
        if (!isPasswordAcceptable(host, config.getPanelPassword(), config.getExecutionMode())) {
            return;
        }
        if (config.getPanelTlsCert().isPresent() != config.getPanelTlsKey().isPresent()) {
            throw new IllegalStateException("panel.tls.cert (PANEL_TLS_CERT) and panel.tls.key (PANEL_TLS_KEY) must be set together");
        }
        if (config.getExecutionMode() == ExecutionMode.SUPERVISED && !isFrontendOnClasspath()) {
            throw new IllegalStateException(("In supervised mode only the operator panel answers the operator prompts, but its frontend "
                    + "(%s/index.html) is missing from the classpath; build the UI module with Maven without -Dskip.npm, e.g. "
                    + "'mvn generate-resources -pl ui_test_execution_agent'").formatted(staticFilesDirectory));
        }
        boolean tls = config.isPanelTlsConfigured();
        var configured = JavalinAppFactory.create(javalinConfig -> configure(javalinConfig, tls));
        app = tls ? configured.start() : configured.start(host, config.getPanelPort());
        long intervalMillis = keepAliveInterval.toMillis();
        keepAliveScheduler.scheduleAtFixedRate(this::keepAlive, intervalMillis, intervalMillis, MILLISECONDS);
        LOG.info("Operator panel started on {}:{} (TLS {})", host, app.port(), tls ? "on" : "off");
        if (!tls) {
            LOG.warn("The operator panel serves plain HTTP; set panel.tls.cert and panel.tls.key for any non-local deployment.");
        }
    }

    int port() {
        if (app == null) {
            throw new IllegalStateException("The operator panel is not running");
        }
        return app.port();
    }

    @Override
    public void close() {
        keepAliveScheduler.shutdownNow();
        keepAliveScheduler.close();
        if (app != null) {
            app.stop();
        }
    }

    boolean isKeepAliveStopped() {
        return keepAliveScheduler.isTerminated();
    }

    static boolean isPasswordAcceptable(@NotNull String host, @NotNull Optional<String> password, @NotNull ExecutionMode executionMode) {
        if (password.isEmpty()) {
            if (executionMode == ExecutionMode.SUPERVISED) {
                throw new IllegalStateException("In supervised mode only the operator panel answers the operator prompts, so "
                        + "panel.password (PANEL_PASSWORD) must be set");
            }
            if (!isLoopback(host)) {
                throw new IllegalStateException(
                        "The operator panel binds to the non-loopback host '%s', so panel.password (PANEL_PASSWORD) must be set"
                                .formatted(host));
            }
            LOG.warn("No operator panel password configured (panel.password / PANEL_PASSWORD); the operator panel is not started.");
            return false;
        }
        if (password.get().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "panel.password (PANEL_PASSWORD) must have at least %d characters".formatted(MIN_PASSWORD_LENGTH));
        }
        return true;
    }

    private static boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            LOG.warn("Could not resolve the operator panel host '{}'; treating it as non-loopback", host, e);
            return false;
        }
    }

    private void configure(JavalinConfig javalinConfig, boolean tls) {
        javalinConfig.jsonMapper(new JavalinJackson(PanelJson.MAPPER, true));
        // Every event stream and every relay pump blocks a thread for as long as it is open
        javalinConfig.concurrency.useVirtualThreads = true;
        javalinConfig.http.maxRequestSize = MAX_REQUEST_SIZE_BYTES;
        javalinConfig.jetty.modifyWebSocketServletFactory(factory -> {
            factory.setMaxBinaryMessageSize(MAX_INBOUND_WEB_SOCKET_BYTES);
            factory.setMaxFrameSize(MAX_INBOUND_WEB_SOCKET_BYTES);
            factory.setIdleTimeout(webSocketIdleTimeout);
        });
        javalinConfig.registerPlugin(new RateLimitPlugin());
        javalinConfig.bundledPlugins.enableGlobalHeaders(headers -> securityHeaders(headers, tls));
        if (tls) {
            javalinConfig.registerPlugin(sslPlugin());
        }
        addStaticFiles(javalinConfig);
        addRoutes(javalinConfig);
    }

    private SslPlugin sslPlugin() {
        var certificate = config.getPanelTlsCert().orElseThrow();
        var privateKey = config.getPanelTlsKey().orElseThrow();
        return new SslPlugin(ssl -> {
            ssl.pemFromString(certificate, privateKey);
            ssl.insecure = false;
            ssl.secure = true;
            ssl.host = config.getPanelHost();
            ssl.securePort = config.getPanelPort();
            // Browsers send no SNI for an IP-address host, and Jetty would answer such a request with 400 Invalid SNI
            ssl.sniHostCheck = false;
            // The event stream and the live view stay on the HTTP/1.1 upgrade path that browsers and Jetty share by default
            ssl.http2 = false;
        });
    }

    private static void securityHeaders(GlobalHeadersConfig headers, boolean tls) {
        headers.contentSecurityPolicy(CONTENT_SECURITY_POLICY);
        headers.xFrameOptions(XFrameOptions.DENY);
        headers.crossOriginOpenerPolicy(CrossOriginOpenerPolicy.SAME_ORIGIN);
        headers.crossOriginResourcePolicy(CrossOriginResourcePolicy.SAME_ORIGIN);
        headers.xContentTypeOptionsNoSniff();
        headers.referrerPolicy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN);
        if (tls) {
            headers.strictTransportSecurity(STRICT_TRANSPORT_SECURITY_MAX_AGE, false);
        }
    }

    private boolean isFrontendOnClasspath() {
        return PanelServer.class.getResource(staticFilesDirectory + "/index.html") != null;
    }

    private void addStaticFiles(JavalinConfig javalinConfig) {
        if (!isFrontendOnClasspath()) {
            LOG.warn("The operator panel frontend is missing from the classpath (built with -Dskip.npm?); only its API is served.");
            return;
        }
        // Vite's hashed file names change with their content, so an unchanged chunk is never downloaded twice
        javalinConfig.staticFiles.add(files -> {
            files.hostedPath = ASSETS_PATH;
            files.directory = staticFilesDirectory + ASSETS_PATH;
            files.location = CLASSPATH;
            files.precompressMaxSize = PRECOMPRESS_ALL_SIZES;
            files.headers = Map.of(CACHE_CONTROL, IMMUTABLE);
        });
        javalinConfig.staticFiles.add(files -> {
            files.hostedPath = "/";
            files.directory = staticFilesDirectory;
            files.location = CLASSPATH;
            files.headers = Map.of(CACHE_CONTROL, NO_CACHE);
        });
    }

    private void addRoutes(JavalinConfig javalinConfig) {
        var routes = javalinConfig.routes;
        routes.before(context -> context.header(PERMISSIONS_POLICY_HEADER, PERMISSIONS_POLICY));
        routes.before("/api/*", context -> context.header(CACHE_CONTROL, NO_STORE));
        routes.before("/api/*", auth::checkApiRequest);
        routes.post(LOGIN_PATH, auth::login);
        routes.post("/api/logout", auth::logout);
        routes.get(SESSION_PATH, auth::sessionProbe);
        routes.sse("/api/events", this::subscribe);
        routes.get("/api/health/graph", graphHealthRoute::handle);
        routes.post(PromptRoutes.ANSWER_PATH, promptRoutes::answer);
        routes.post(PromptRoutes.HOLD_PATH, promptRoutes::hold);
        routes.post(PromptRoutes.PAUSE_PATH, promptRoutes::pause);
        routes.post(PromptRoutes.RESUME_PATH, promptRoutes::resume);
        routes.post(PromptRoutes.ACTION_PATH, promptRoutes::action);
        routes.get(PromptRoutes.IMAGE_PATH, promptRoutes::image);
        routes.get(KnowledgeRoutes.SUMMARY_PATH, knowledgeRoutes::summary);
        routes.get(KnowledgeRoutes.SEARCH_PATH, knowledgeRoutes::search);
        routes.get(KnowledgeRoutes.NEIGHBOURHOOD_PATH, knowledgeRoutes::neighbourhood);
        routes.get(KnowledgeRoutes.TEST_CASE_LENS_PATH, knowledgeRoutes::testCaseLens);
        routes.get(KnowledgeRoutes.PROCEDURE_PATH, knowledgeRoutes::procedure);
        routes.get(KnowledgeRoutes.ELEMENTS_PATH, knowledgeRoutes::elements);
        routes.get(KnowledgeRoutes.ELEMENT_PATH, knowledgeRoutes::element);
        routes.get(KnowledgeRoutes.ELEMENT_SCREENSHOT_PATH, knowledgeRoutes::elementScreenshot);
        routes.get(KnowledgeRoutes.APPEARANCE_SCREENSHOT_PATH, knowledgeRoutes::appearanceScreenshot);
        routes.get(KnowledgeRoutes.OVERVIEW_PATH, knowledgeRoutes::overview);
        routes.get(KnowledgeRoutes.TRENDS_PATH, knowledgeRoutes::trends);
        routes.get(KnowledgeRoutes.FAILURES_PATH, knowledgeRoutes::failures);
        if (relay.isEnabled()) {
            routes.wsBeforeUpgrade("/api/screen/*", auth::checkUpgradeRequest);
            routes.ws("/api/screen/live", ws -> {
                ws.onConnect(relay::onConnect);
                ws.onBinaryMessage(relay::onBinaryMessage);
                ws.onClose(relay::onClose);
                ws.onError(relay::onClose);
            });
        }
    }

    private void subscribe(SseClient client) {
        var context = client.ctx();
        var sessionId = auth.sessionIdOf(context).orElseThrow(() -> new IllegalStateException("Event stream without a session"));
        client.keepAlive();
        broadcaster.subscribe(client, sessionId, auth.displacedBy(context, Reason.OTHER_TAB));
    }

    // A failing tick would otherwise cancel every later one
    private void keepAlive() {
        try {
            broadcaster.keepAlive();
            relay.keepAlive();
        } catch (RuntimeException e) {
            LOG.warn("The operator panel keep-alive tick failed", e);
        }
    }
}
