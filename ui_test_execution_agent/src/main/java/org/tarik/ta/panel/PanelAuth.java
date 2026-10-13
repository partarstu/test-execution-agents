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

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.SameSite;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.plugin.bundled.RateLimitPlugin;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.dto.panel.PanelEvent.Displaced;
import org.tarik.ta.dto.panel.PanelEvent.Displaced.Reason;
import org.tarik.ta.dto.panel.SessionInfo;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static io.javalin.http.HandlerType.GET;
import static io.javalin.http.HttpStatus.FORBIDDEN;
import static io.javalin.http.HttpStatus.NO_CONTENT;
import static io.javalin.http.HttpStatus.UNAUTHORIZED;
import static io.javalin.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Login, the single operator session and the checks every panel request passes. Thread-safe: the session lives in atomics, and
 * logins are serialized so a takeover always notifies the replaced session before it stops being valid.
 */
@Singleton
final class PanelAuth {
    private static final Logger LOG = LoggerFactory.getLogger(PanelAuth.class);
    static final String TLS_COOKIE_NAME = "__Host-panel_session";
    static final String PLAIN_COOKIE_NAME = "panel_session";
    static final String LOGIN_PATH = "/api/login";
    static final String SESSION_PATH = "/api/session";
    private static final String SESSION_ID_ATTRIBUTE = "panelSessionId";
    private static final Set<String> PATHS_WITHOUT_SESSION = Set.of(LOGIN_PATH, SESSION_PATH);
    private static final int SESSION_ID_BYTES = 32;
    private static final int LOGIN_ATTEMPTS_PER_MINUTE = 5;
    private static final int MAX_BROWSER_LENGTH = 200;
    private static final int SESSION_COOKIE_MAX_AGE = -1;
    private static final int EXPIRED_COOKIE_MAX_AGE = 0;
    private static final String SAME_ORIGIN = "same-origin";
    private static final String UNKNOWN_BROWSER = "unknown";
    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String CLEAR_SITE_DATA_HEADER = "Clear-Site-Data";

    private final byte[] expectedPassword;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;
    private final InstantSource clock;
    private final boolean tls;
    private final SessionInfo sessionInfo;
    private final SecureRandom random = new SecureRandom();
    private final AtomicReference<PanelSession> currentSession = new AtomicReference<>();
    private final AtomicReference<DisplacedSession> lastDisplaced = new AtomicReference<>();
    private final List<SessionEndListener> sessionEndListeners = new CopyOnWriteArrayList<>();
    private final ReentrantLock loginLock = new ReentrantLock();

    @Inject
    PanelAuth(UiTestAgentConfig config) {
        this(config.getPanelPassword().orElse(""), config.getPanelSessionIdleTimeout(), config.getPanelSessionAbsoluteTimeout(),
                InstantSource.system(), config.isPanelTlsConfigured(),
                new SessionInfo(config.getExecutionMode(), config.getVncSocketPath().isPresent(), config.isPanelLiveControlEnabled(),
                        config.getProcedureLookupDelayMs()));
    }

    PanelAuth(@NotNull String password, @NotNull Duration idleTimeout, @NotNull Duration absoluteTimeout, @NotNull InstantSource clock,
              boolean tls, @NotNull SessionInfo sessionInfo) {
        this.expectedPassword = password.getBytes(UTF_8);
        this.idleTimeout = idleTimeout;
        this.absoluteTimeout = absoluteTimeout;
        this.clock = clock;
        this.tls = tls;
        this.sessionInfo = sessionInfo;
    }

    /** Called with the id of every session that ends, and with the notice for its tab when it was taken over. */
    @FunctionalInterface
    interface SessionEndListener {
        void sessionEnded(@NotNull String sessionId, @Nullable Displaced displaced);
    }

    void addSessionEndListener(@NotNull SessionEndListener listener) {
        sessionEndListeners.add(listener);
    }

    String cookieName() {
        return tls ? TLS_COOKIE_NAME : PLAIN_COOKIE_NAME;
    }

    /** Before-handler of {@code /api/*}: rejects cross-site writes and non-JSON bodies, then requests without a valid session. */
    void checkApiRequest(@NotNull Context context) {
        if (context.method() != GET && !isSameOrigin(context)) {
            context.status(FORBIDDEN).skipRemainingHandlers();
            return;
        }
        if (hasBody(context) && !isJson(context)) {
            context.status(UNSUPPORTED_MEDIA_TYPE).skipRemainingHandlers();
            return;
        }
        if (PATHS_WITHOUT_SESSION.contains(context.path())) {
            return;
        }
        find(context.cookie(cookieName())).ifPresentOrElse(session -> {
            touch(session.id());
            context.attribute(SESSION_ID_ATTRIBUTE, session.id());
        }, () -> context.status(UNAUTHORIZED).skipRemainingHandlers());
    }

    void checkUpgradeRequest(@NotNull Context context) {
        if (!isSameOrigin(context)) {
            throw new ForbiddenResponse();
        }
        if (find(context.cookie(cookieName())).isEmpty()) {
            throw new UnauthorizedResponse();
        }
    }

    void login(@NotNull Context context) {
        context.with(RateLimitPlugin.class).requestPerTimeUnit(LOGIN_ATTEMPTS_PER_MINUTE, MINUTES);
        var request = context.bodyValidator(LoginRequest.class).get();
        if (request.password() == null) {
            throw new BadRequestResponse("The password is required");
        }
        if (!isExpectedPassword(request.password())) {
            LOG.info("Failed operator panel login from {}", context.ip());
            context.status(UNAUTHORIZED).json(Map.of("error", "Invalid password"));
            return;
        }
        var now = clock.instant();
        var session = new PanelSession(newSessionId(), now, now, context.ip(), browserOf(context));
        replaceSession(session, now);
        context.cookie(sessionCookie(session.id(), SESSION_COOKIE_MAX_AGE));
        context.json(sessionInfo);
        LOG.info("Operator panel login from {}", session.sourceIp());
    }

    void logout(@NotNull Context context) {
        find(context.attribute(SESSION_ID_ATTRIBUTE)).ifPresent(session -> {
            endIf(session.id(), _ -> true);
            LOG.info("Operator panel logout from {}", context.ip());
        });
        context.cookie(sessionCookie("", EXPIRED_COOKIE_MAX_AGE));
        context.header(CLEAR_SITE_DATA_HEADER, "\"cookies\", \"storage\"");
        context.status(NO_CONTENT);
    }

    /** Answers the flags for a valid session; otherwise 401, carrying the notice when the cookie names the last replaced session. */
    void sessionProbe(@NotNull Context context) {
        var sessionId = context.cookie(cookieName());
        var session = find(sessionId);
        if (session.isPresent()) {
            touch(session.get().id());
            context.json(sessionInfo);
            return;
        }
        context.status(UNAUTHORIZED);
        displacementOf(sessionId).ifPresent(context::json);
    }

    @NotNull Optional<String> sessionIdOf(@NotNull Context context) {
        return Optional.ofNullable(context.attribute(SESSION_ID_ATTRIBUTE));
    }

    @NotNull Optional<String> activeSessionId(@Nullable String cookieValue) {
        return find(cookieValue).map(PanelSession::id);
    }

    boolean isActive(@NotNull String sessionId) {
        return find(sessionId).isPresent();
    }

    void touch(@NotNull String sessionId) {
        var now = clock.instant();
        currentSession.updateAndGet(session -> session != null && session.id().equals(sessionId) ? session.seenAt(now) : session);
    }

    @NotNull Displaced displacedBy(@NotNull Context context, @NotNull Reason reason) {
        return new Displaced(reason, clock.instant(), context.ip(), browserOf(context));
    }

    static boolean isSameOrigin(@Nullable String fetchSite, @Nullable String origin, @NotNull String ownOrigin) {
        if (fetchSite != null) {
            return SAME_ORIGIN.equals(fetchSite);
        }
        return ownOrigin.equals(origin);
    }

    private static boolean isSameOrigin(Context context) {
        var ownOrigin = "%s://%s".formatted(context.scheme(), context.host());
        return isSameOrigin(context.header("Sec-Fetch-Site"), context.header("Origin"), ownOrigin);
    }

    private static boolean hasBody(Context context) {
        return context.contentLength() > 0 || context.header("Transfer-Encoding") != null;
    }

    private static boolean isJson(Context context) {
        var contentType = context.contentType();
        return contentType != null && contentType.toLowerCase(ROOT).startsWith(JSON_MEDIA_TYPE);
    }

    // An empty expected password would accept an empty login; the server never starts with one, this keeps it that way
    private boolean isExpectedPassword(String providedPassword) {
        return expectedPassword.length > 0 && MessageDigest.isEqual(expectedPassword, providedPassword.getBytes(UTF_8));
    }

    private void replaceSession(PanelSession session, Instant now) {
        loginLock.lock();
        try {
            var previous = currentSession.get();
            if (previous != null && !isExpired(previous, now)) {
                var displaced = new Displaced(Reason.TAKEN_OVER, now, session.sourceIp(), session.browser());
                lastDisplaced.set(new DisplacedSession(previous.id(), displaced, previous.createdAt().plus(absoluteTimeout)));
                // The listeners run while the old session is still valid, so its event stream can still deliver the notice
                notifyListeners(previous.id(), displaced);
                LOG.info("Operator panel session taken over: the session of {} was replaced by a login from {}", previous.sourceIp(),
                        session.sourceIp());
            } else if (previous != null) {
                notifyListeners(previous.id(), null);
            }
            currentSession.set(session);
        } finally {
            loginLock.unlock();
        }
    }

    private Optional<PanelSession> find(@Nullable String sessionId) {
        var session = currentSession.get();
        if (sessionId == null || session == null || !isSameSecret(session.id(), sessionId)) {
            return Optional.empty();
        }
        var now = clock.instant();
        if (isExpired(session, now)) {
            if (endIf(session.id(), current -> isExpired(current, now))) {
                LOG.info("Operator panel session of {} expired", session.sourceIp());
            }
            return Optional.empty();
        }
        return Optional.of(session);
    }

    private boolean isExpired(PanelSession session, Instant now) {
        return now.isAfter(session.lastSeen().plus(idleTimeout)) || now.isAfter(session.createdAt().plus(absoluteTimeout));
    }

    // Matches the session by id and tests the current record, because every touch replaces the record a caller looked up earlier
    private boolean endIf(String sessionId, Predicate<PanelSession> condition) {
        UnaryOperator<PanelSession> removal = current ->
                current != null && current.id().equals(sessionId) && condition.test(current) ? null : current;
        var previous = currentSession.getAndUpdate(removal);
        boolean ended = previous != null && removal.apply(previous) == null;
        if (ended) {
            notifyListeners(sessionId, null);
        }
        return ended;
    }

    private void notifyListeners(String sessionId, @Nullable Displaced displaced) {
        sessionEndListeners.forEach(listener -> listener.sessionEnded(sessionId, displaced));
    }

    private Optional<Displaced> displacementOf(@Nullable String sessionId) {
        var displacedSession = lastDisplaced.get();
        if (sessionId == null || displacedSession == null || !isSameSecret(displacedSession.sessionId(), sessionId)
                || clock.instant().isAfter(displacedSession.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(displacedSession.displaced());
    }

    private static boolean isSameSecret(String expected, String provided) {
        return MessageDigest.isEqual(expected.getBytes(UTF_8), provided.getBytes(UTF_8));
    }

    private String newSessionId() {
        var bytes = new byte[SESSION_ID_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String browserOf(Context context) {
        var userAgent = context.userAgent();
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN_BROWSER;
        }
        return userAgent.length() > MAX_BROWSER_LENGTH ? userAgent.substring(0, MAX_BROWSER_LENGTH) : userAgent;
    }

    private Cookie sessionCookie(String value, int maxAge) {
        var cookie = new Cookie(cookieName(), value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setSecure(tls);
        cookie.setHttpOnly(true);
        cookie.setSameSite(SameSite.STRICT);
        return cookie;
    }

    record LoginRequest(String password) {
        @Override
        public @NotNull String toString() {
            return "LoginRequest[password=***]";
        }
    }

    private record PanelSession(String id, Instant createdAt, Instant lastSeen, String sourceIp, String browser) {
        PanelSession seenAt(Instant instant) {
            return new PanelSession(id, createdAt, instant, sourceIp, browser);
        }

        @Override
        public @NotNull String toString() {
            return "PanelSession[createdAt=%s, lastSeen=%s, sourceIp=%s]".formatted(createdAt, lastSeen, sourceIp);
        }
    }

    private record DisplacedSession(String sessionId, Displaced displaced, Instant expiresAt) {
        @Override
        public @NotNull String toString() {
            return "DisplacedSession[displaced=%s, expiresAt=%s]".formatted(displaced, expiresAt);
        }
    }
}
