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

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.protocol.HttpContext;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.ApiTestAgentConfig;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
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
    private static final String HTTP_URL_PREFIX = "http://";
    private static final String HTTPS_URL_PREFIX = "https://";

    private final Set<String> hostAllowlist;
    private final Optional<String> uploadBaseDir;

    OutboundRequestGuard(@NotNull Set<String> hostAllowlist, @NotNull Optional<String> uploadBaseDir) {
        this.hostAllowlist = hostAllowlist;
        this.uploadBaseDir = uploadBaseDir;
    }

    static OutboundRequestGuard from(@NotNull ApiTestAgentConfig config) {
        return new OutboundRequestGuard(config.getOutboundHostAllowlist(), config.getUploadBaseDir());
    }

    static boolean isAbsoluteHttpUrl(@NotNull String url) {
        return startsWithIgnoreCase(url, HTTP_URL_PREFIX) || startsWithIgnoreCase(url, HTTPS_URL_PREFIX);
    }

    private static boolean startsWithIgnoreCase(String text, String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * Rejects requests whose target host is not permitted. An explicitly configured allow-list acts as a trusted
     * override: a host on the list is allowed, a host not on it is refused. When no allow-list is configured, the
     * host's resolved IPs are checked against the SSRF-sensitive ranges (loopback, link-local, site-local, multicast,
     * wildcard, 0.0.0.0/8, carrier-grade NAT, IPv6 unique-local) and the cloud metadata address.
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

    /** Creates the HTTP client of one request, which checks the target of every redirect with {@link #assertHostAllowed}. */
    HttpClient newRedirectGuardedHttpClient() {
        // REST Assured applies its settings through HttpParams, which only the legacy DefaultHttpClient supports
        @SuppressWarnings("deprecation")
        var httpClient = new DefaultHttpClient();
        httpClient.setRedirectStrategy(new DefaultRedirectStrategy() {
            @Override
            public URI getLocationURI(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
                var location = super.getLocationURI(request, response, context);
                assertHostAllowed(location.toString());
                return location;
            }
        });
        return httpClient;
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
                || CLOUD_METADATA_IP.equals(address.getHostAddress())
                || isInThisNetwork(address)
                || isCarrierGradeNat(address)
                || isIpv6UniqueLocal(address);
    }

    /** 0.0.0.0/8, which many stacks route to the local host. */
    private static boolean isInThisNetwork(@NotNull InetAddress address) {
        return address instanceof Inet4Address && address.getAddress()[0] == 0;
    }

    /** 100.64.0.0/10, the shared address space of carrier-grade NAT and of some cloud-internal services. */
    private static boolean isCarrierGradeNat(@NotNull InetAddress address) {
        byte[] bytes = address.getAddress();
        return address instanceof Inet4Address && bytes[0] == 100 && (bytes[1] & 0xC0) == 0x40;
    }

    /** fc00::/7, the IPv6 private range that also holds the AWS metadata address fd00:ec2::254. */
    private static boolean isIpv6UniqueLocal(@NotNull InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xFE) == 0xFC;
    }

    private static ToolExecutionException blocked(@NotNull String message) {
        return new ToolExecutionException(message, NON_RETRYABLE_ERROR);
    }
}
