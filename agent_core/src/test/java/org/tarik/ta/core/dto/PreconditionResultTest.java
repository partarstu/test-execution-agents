/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
