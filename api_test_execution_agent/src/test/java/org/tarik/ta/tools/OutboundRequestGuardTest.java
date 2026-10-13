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

import org.junit.jupiter.api.Test;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundRequestGuardTest {
    private static final String BLOCKED_ADDRESS = "blocked address";

    private final OutboundRequestGuard guard = new OutboundRequestGuard(Set.of(), Optional.empty());

    @Test
    void assertHostAllowed_shouldBlockIpv6Loopback() {
        assertRejected("http://[::1]:8080/", BLOCKED_ADDRESS);
    }

    @Test
    void assertHostAllowed_shouldBlockIpv6LinkLocal() {
        assertRejected("http://[fe80::1]/", BLOCKED_ADDRESS);
    }

    @Test
    void assertHostAllowed_shouldBlockIpv6UniqueLocal() {
        assertRejected("http://[fc00::1]/", BLOCKED_ADDRESS);
    }

    @Test
    void assertHostAllowed_shouldBlockTheAwsIpv6MetadataAddress() {
        assertRejected("http://[fd00:ec2::254]/latest/meta-data/", BLOCKED_ADDRESS);
    }

    @Test
    void assertHostAllowed_shouldBlockIpv4MappedLoopback() {
        assertRejected("http://[::ffff:127.0.0.1]/", BLOCKED_ADDRESS);
    }

    @Test
    void assertHostAllowed_shouldBlockTheWildcardAddress() {
        assertRejected("http://0.0.0.0:8080/", BLOCKED_ADDRESS);
    }

    @Test
    void assertHostAllowed_shouldBlockTheThisNetworkRange() {
        assertRejected("http://0.1.2.3/", BLOCKED_ADDRESS);
    }

    @Test
    void assertHostAllowed_shouldBlockCarrierGradeNatAddresses() {
        assertRejected("http://100.64.0.1/", BLOCKED_ADDRESS);
        assertRejected("http://100.127.255.254/", BLOCKED_ADDRESS);
    }

    @Test
    void assertHostAllowed_shouldAllowPublicAddressesAroundTheCarrierGradeNatRange() {
        assertThatCode(() -> guard.assertHostAllowed("http://100.63.255.255/")).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertHostAllowed("http://100.128.0.1/")).doesNotThrowAnyException();
    }

    @Test
    void assertHostAllowed_shouldRejectDecimalAndHexLoopbackLiterals() {
        // The JDK refuses these as IP literals and passes them to the OS resolver, which either fails or maps them to 127.0.0.1
        assertThatThrownBy(() -> guard.assertHostAllowed("http://2130706433/")).isInstanceOf(ToolExecutionException.class);
        assertThatThrownBy(() -> guard.assertHostAllowed("http://0x7f000001/")).isInstanceOf(ToolExecutionException.class);
    }

    @Test
    void assertHostAllowed_shouldRejectRelativeUrls() {
        assertRejected("/internal/admin", "has no resolvable host");
    }

    @Test
    void assertHostAllowed_shouldRejectMalformedUrls() {
        assertRejected("http://exa mple.com/", "Invalid URL");
    }

    @Test
    void isAbsoluteHttpUrl_shouldAcceptOnlyHttpAndHttpsUrlsInAnyCase() {
        assertThat(OutboundRequestGuard.isAbsoluteHttpUrl("HTTPS://api.example.com/pets")).isTrue();
        assertThat(OutboundRequestGuard.isAbsoluteHttpUrl("http://api.example.com/pets")).isTrue();
        assertThat(OutboundRequestGuard.isAbsoluteHttpUrl("/pets")).isFalse();
        assertThat(OutboundRequestGuard.isAbsoluteHttpUrl("file:///etc/passwd")).isFalse();
    }

    private void assertRejected(String url, String expectedMessage) {
        assertThatThrownBy(() -> guard.assertHostAllowed(url))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining(expectedMessage);
    }
}
