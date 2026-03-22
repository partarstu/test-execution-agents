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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DtoTest {

    @Test
    void testEmptyExecutionResult() {
        EmptyExecutionResult result = new EmptyExecutionResult();
        assertThat(result).isNotNull();
        assertThat(EmptyExecutionResult.endExecutionAndGetFinalResult()).isNull();
    }

    @Test
    void testVerificationExecutionResult() {
        VerificationExecutionResult result = new VerificationExecutionResult(true, "success");
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("success");
        assertThat(VerificationExecutionResult.endExecutionAndGetFinalResult(result)).isEqualTo(result);
    }

    @Test
    void testSystemInfo() {
        SystemInfo info = new SystemInfo("device", "os", "browser", "env");
        assertThat(info.device()).isEqualTo("device");
        assertThat(info.osVersion()).isEqualTo("os");
        assertThat(info.browser()).isEqualTo("browser");
        assertThat(info.environment()).isEqualTo("env");
    }

    @Test
    void testPreconditionExecutionActionResult() {
        PreconditionExecutionActionResult result = new PreconditionExecutionActionResult("summary");
        assertThat(result.executionSummary()).isEqualTo("summary");
        assertThat(PreconditionExecutionActionResult.endExecutionAndGetFinalResult(result)).isEqualTo(result);
    }

    @Test
    void testTestCase() {
        TestCase testCase = new TestCase("name", List.of("p1"), List.of());
        assertThat(testCase.name()).isEqualTo("name");
        assertThat(testCase.preconditions()).containsExactly("p1");
        assertThat(testCase.testSteps()).isEmpty();
        assertThat(TestCase.endExecutionAndGetFinalResult(testCase)).isEqualTo(testCase);
    }
}
