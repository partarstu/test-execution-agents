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

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/** Stands in for Xvnc: a Unix domain socket that accepts the relay's connections and lets a test read and write raw bytes. */
public final class XvncStub implements AutoCloseable {
    private final Path socketPath;
    private final ServerSocketChannel serverChannel;
    private final ExecutorService acceptor = newVirtualThreadPerTaskExecutor();
    private final BlockingQueue<SocketChannel> connections = new LinkedBlockingQueue<>();

    private XvncStub(Path socketPath) throws IOException {
        this.socketPath = socketPath;
        Files.deleteIfExists(socketPath);
        this.serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
        acceptor.submit(this::acceptConnections);
    }

    public static XvncStub listen(@NotNull Path socketPath) throws IOException {
        return new XvncStub(socketPath);
    }

    public @NotNull String socketPath() {
        return socketPath.toString();
    }

    public @NotNull SocketChannel awaitConnection(@NotNull Duration timeout) throws InterruptedException {
        var connection = connections.poll(timeout.toMillis(), MILLISECONDS);
        if (connection == null) {
            throw new AssertionError("The relay did not connect to the Xvnc socket in time");
        }
        return connection;
    }

    public static void write(@NotNull SocketChannel connection, byte @NotNull [] bytes) throws IOException {
        var buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            connection.write(buffer);
        }
    }

    public static byte @NotNull [] read(@NotNull SocketChannel connection, int length) throws IOException {
        var buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            if (connection.read(buffer) < 0) {
                throw new AssertionError("The relay closed the Xvnc connection after %d of %d bytes".formatted(buffer.position(), length));
            }
        }
        return buffer.array();
    }

    /** Returns true once the relay has closed the connection, which a blocking read observes as end of stream. */
    public static boolean isClosedByPeer(@NotNull SocketChannel connection) throws IOException {
        return connection.read(ByteBuffer.allocate(1)) < 0;
    }

    @Override
    public void close() throws IOException {
        serverChannel.close();
        acceptor.shutdownNow();
        for (var connection : connections) {
            connection.close();
        }
        Files.deleteIfExists(socketPath);
    }

    private void acceptConnections() {
        try {
            while (serverChannel.isOpen()) {
                connections.add(serverChannel.accept());
            }
        } catch (IOException _) {
            // The stub was closed
        }
    }
}
