/*
 * agent-core - ${project.description}
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
package org.tarik.ta.core.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PreconditionResultTest {

    @Test
    void testAccessors() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(1);
        PreconditionResult result = new PreconditionResult("pre", true, "err", start, end);

        assertThat(result.getPrecondition()).isEqualTo("pre");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorMessage()).isEqualTo("err");
        assertThat(result.getExecutionStartTimestamp()).isEqualTo(start);
        assertThat(result.getExecutionEndTimestamp()).isEqualTo(end);
    }

    @Test
    void testEqualsAndHashCode() {
        Instant now = Instant.now();
        PreconditionResult r1 = new PreconditionResult("p", true, "e", now, now);
        PreconditionResult r2 = new PreconditionResult("p", true, "e", now, now);
        PreconditionResult r3 = new PreconditionResult("x", false, "y", null, null);

        assertThat(r1).isEqualTo(r2);
        assertThat(r1).hasSameHashCodeAs(r2);
        assertThat(r1).isNotEqualTo(r3);
        assertThat(r1).isNotEqualTo(null);
    }

    @Test
    void testToString() {
        PreconditionResult result = new PreconditionResult("p", true, "e", null, null);
        assertThat(result.toString()).contains("PreconditionResult", "p", "true", "e");
    }
}
