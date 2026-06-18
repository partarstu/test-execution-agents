/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.tarik.ta.core.error.ErrorCategory.NON_RETRYABLE_ERROR;

/**
 * Enforces outbound-request and file-access restrictions at the tool layer, independent of the LLM or any
 * upstream authentication. This guards against SSRF (e.g. reaching cloud metadata or internal services) and
 * local-file exfiltration regardless of what an attacker manages to instruct the agent to do.
 */
class OutboundRequestGuard {
    private static final String CLOUD_METADATA_IP = "169.254.169.254";

    private final Set<String> hostAllowlist;
    private final Optional<String> uploadBaseDir;

    OutboundRequestGuard(@NotNull Set<String> hostAllowlist, @NotNull Optional<String> uploadBaseDir) {
        this.hostAllowlist = hostAllowlist;
        this.uploadBaseDir = uploadBaseDir;
    }

    /**
     * Rejects requests whose target host is not permitted. An explicitly configured allow-list acts as a trusted
     * override: a host on the list is allowed, a host not on it is refused. When no allow-list is configured, the
     * host's resolved IPs are checked against the SSRF-sensitive ranges (loopback, link-local, site-local, multicast,
     * wildcard) and the cloud metadata address.
     */
    void assertHostAllowed(@NotNull String url) {
        String host = extractHost(url);
        boolean allowlistConfigured = !hostAllowlist.isEmpty();
        boolean hostAllowlisted = allowlistConfigured && hostAllowlist.contains(host.toLowerCase(Locale.ROOT));
        if (allowlistConfigured && !hostAllowlisted) {
            throw blocked("Host '%s' is not in the configured outbound allow-list".formatted(host));
        }
        if (!hostAllowlisted) {
            assertNoBlockedAddress(host);
        }
    }

    /**
     * Rejects file uploads outside the configured upload directory. Both the configured base directory and the target
     * file are canonicalized via {@link Path#toRealPath} so that symlinks and {@code ..} traversal cannot escape it. If
     * no upload directory is configured, uploads are refused entirely.
     */
    void assertPathAllowed(@NotNull String filePath) {
        String configuredBaseDir = uploadBaseDir.orElseThrow(() -> blocked(
                "File upload is disabled because no upload base directory is configured (api.upload.base.dir)"));
        Path baseDir;
        Path realPath;
        try {
            baseDir = Paths.get(configuredBaseDir).toRealPath();
            realPath = Paths.get(filePath).toRealPath();
        } catch (IOException e) {
            throw blocked("Cannot resolve file path '%s': %s".formatted(filePath, e.getMessage()));
        }
        if (!realPath.startsWith(baseDir)) {
            throw blocked("File '%s' is outside the allowed upload directory '%s'".formatted(filePath, baseDir));
        }
    }

    private static String extractHost(@NotNull String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw blocked("Invalid URL '%s': %s".formatted(url, e.getMessage()));
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw blocked("URL '%s' has no resolvable host".formatted(url));
        }
        return host;
    }

    private static void assertNoBlockedAddress(@NotNull String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw blocked("Cannot resolve host '%s'".formatted(host));
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw blocked("Outbound request to a blocked address (%s -> %s) is not allowed"
                        .formatted(host, address.getHostAddress()));
            }
        }
    }

    private static boolean isBlockedAddress(@NotNull InetAddress address) {
        return address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || address.isAnyLocalAddress()
                || CLOUD_METADATA_IP.equals(address.getHostAddress());
    }

    private static ToolExecutionException blocked(@NotNull String message) {
        return new ToolExecutionException(message, NON_RETRYABLE_ERROR);
    }
}
