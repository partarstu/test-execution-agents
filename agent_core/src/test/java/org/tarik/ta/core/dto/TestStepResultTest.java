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
