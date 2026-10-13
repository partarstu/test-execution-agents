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

import io.javalin.websocket.WsBinaryMessageContext;
import io.javalin.websocket.WsCloseStatus;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.jetty.websocket.api.Callback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Relays the bytes of the live view between the browser's WebSocket and the Xvnc Unix domain socket without looking at them; Xvnc
 * itself enforces what a viewer may do. Thread-safe: at most one connection exists, and a new one closes the previous one.
 */
@Singleton
final class VncRelay implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VncRelay.class);
    private static final int READ_BUFFER_BYTES = 64 * 1024;
    static final Duration WEB_SOCKET_IDLE_TIMEOUT = Duration.ofMinutes(5);

    private final @Nullable UnixDomainSocketAddress socketAddress;
    private final PanelAuth auth;
    private final Duration sendTimeout;
    private final ExecutorService pumpExecutor = newVirtualThreadPerTaskExecutor();
    private final AtomicReference<Connection> connection = new AtomicReference<>();

    @Inject
    VncRelay(UiTestAgentConfig config, PanelAuth auth) {
        this(config.getVncSocketPath().orElse(null), auth, WEB_SOCKET_IDLE_TIMEOUT);
    }

    VncRelay(@Nullable String socketPath, @NotNull PanelAuth auth, @NotNull Duration sendTimeout) {
        this.socketAddress = socketPath == null ? null : UnixDomainSocketAddress.of(socketPath);
        this.auth = auth;
        this.sendTimeout = sendTimeout;
    }

    boolean isEnabled() {
        return socketAddress != null;
    }

    void onConnect(@NotNull WsConnectContext context) {
        Optional<String> sessionId = auth.activeSessionId(context.cookie(auth.cookieName()));
        if (sessionId.isEmpty() || socketAddress == null) {
            context.closeSession(WsCloseStatus.POLICY_VIOLATION);
            return;
        }
        SocketChannel channel;
        try {
            channel = SocketChannel.open(socketAddress);
        } catch (IOException e) {
            LOG.error("Could not connect the live view to the Xvnc socket {}", socketAddress, e);
            context.closeSession(WsCloseStatus.SERVER_ERROR);
            return;
        }
        var opened = new Connection(context, sessionId.get(), channel);
        var replaced = connection.getAndSet(opened);
        if (replaced != null) {
            replaced.close();
        }
        opened.pump = pumpExecutor.submit(() -> pump(opened));
        LOG.info("Live view connection opened{}", replaced == null ? "" : ", replacing the previous one");
    }

    void onBinaryMessage(@NotNull WsBinaryMessageContext context) {
        var current = connectionOf(context);
        if (current == null) {
            return;
        }
        var data = context.data();
        try {
            while (data.hasRemaining()) {
                current.channel.write(data);
            }
        } catch (IOException e) {
            LOG.debug("Could not forward live view input to Xvnc", e);
            current.close();
        }
    }

    void onClose(@NotNull WsContext context) {
        var current = connectionOf(context);
        if (current != null) {
            current.close();
        }
    }

    /** Pings the open connection so a quiet view outlives the idle timeout, and ends it once its session is gone. */
    void keepAlive() {
        var current = connection.get();
        if (current == null) {
            return;
        }
        if (!auth.isActive(current.sessionId)) {
            current.close();
            return;
        }
        current.context.sendPing();
    }

    void closeSession(@NotNull String sessionId) {
        var current = connection.get();
        if (current != null && current.sessionId.equals(sessionId)) {
            current.close();
        }
    }

    @Override
    public void close() {
        var current = connection.get();
        if (current != null) {
            current.close();
        }
        pumpExecutor.shutdownNow();
        pumpExecutor.close();
    }

    // A slow browser stalls this loop at the send, so Xvnc's own deferred updates absorb the backlog instead of the heap
    private void pump(Connection current) {
        var buffer = ByteBuffer.allocate(READ_BUFFER_BYTES);
        try {
            while (current.channel.read(buffer.clear()) >= 0) {
                if (!auth.isActive(current.sessionId)) {
                    return;
                }
                var sent = new Callback.Completable();
                current.context.session.sendBinary(buffer.flip(), sent);
                sent.get(sendTimeout.toMillis(), MILLISECONDS);
            }
        } catch (IOException | ExecutionException | TimeoutException e) {
            LOG.debug("Live view relay stopped", e);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } finally {
            current.close();
        }
    }

    private @Nullable Connection connectionOf(WsContext context) {
        var current = connection.get();
        return current != null && current.context.sessionId().equals(context.sessionId()) ? current : null;
    }

    private final class Connection {
        private final WsContext context;
        private final String sessionId;
        private final SocketChannel channel;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile @Nullable Future<?> pump;

        private Connection(WsContext context, String sessionId, SocketChannel channel) {
            this.context = context;
            this.sessionId = sessionId;
            this.channel = channel;
        }

        private void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            connection.compareAndSet(this, null);
            try {
                channel.close();
            } catch (IOException e) {
                LOG.debug("Could not close the Xvnc socket channel", e);
            }
            if (context.session.isOpen()) {
                context.closeSession();
            }
            var pumpTask = pump;
            if (pumpTask != null) {
                pumpTask.cancel(true);
            }
            LOG.info("Live view connection closed");
        }
    }
}
