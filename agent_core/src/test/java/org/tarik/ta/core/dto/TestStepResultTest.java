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
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.FAILURE;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;
import java.util.Collections;

class TestStepResultTest {

    @Test
    void shouldInitializeCorrectly() {
        TestStep step = new TestStep("Action", Collections.emptyList(), "Verification");
        TestStepResult.TestStepResultStatus status = SUCCESS;
        String error = "None";
        String actual = "Done";
        Instant start = Instant.now();
        Instant end = start.plusSeconds(1);

        TestStepResult result = new TestStepResult(step, status, error, actual, start, end);

        assertThat(result.getTestStep()).isEqualTo(step);
        assertThat(result.getExecutionStatus()).isEqualTo(status);
        assertThat(result.getErrorMessage()).isEqualTo(error);
        assertThat(result.getActualResult()).isEqualTo(actual);
        assertThat(result.getExecutionStartTimestamp()).isEqualTo(start);
        assertThat(result.getExecutionEndTimestamp()).isEqualTo(end);
    }

    @Test
    void testEqualsAndHashCode() {
        TestStep step = new TestStep("Action", Collections.emptyList(), "Verification");
        Instant now = Instant.now();
        TestStepResult result1 = new TestStepResult(step, SUCCESS, null, null, now, now);
        TestStepResult result2 = new TestStepResult(step, SUCCESS, null, null, now, now);
        TestStepResult result3 = new TestStepResult(step, FAILURE, "Error", null, now, now);

        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        assertThat(result1).isNotEqualTo(result3);
    }

    @Test
    void testToString_Success() {
        TestStep step = new TestStep("Action", Collections.emptyList(), "Verification");
        TestStepResult result = new TestStepResult(step, SUCCESS, null, null, Instant.now(), Instant.now());

        String str = result.toString();
        assertThat(str).contains("TestStepResult:");
        assertThat(str).contains("Step: " + step);
        assertThat(str).contains("Status: SUCCESS");
        assertThat(str).doesNotContain("Error:");
    }

    @Test
    void testToString_Failure() {
        TestStep step = new TestStep("Action", Collections.emptyList(), "Verification");
        TestStepResult result = new TestStepResult(step, FAILURE, "Some error", null, Instant.now(), Instant.now());

        String str = result.toString();
        assertThat(str).contains("Status: FAILURE");
        assertThat(str).contains("Error: Some error");
    }
}
