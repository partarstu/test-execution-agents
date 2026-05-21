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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DtoTest {

    @Test
    void testEmptyExecutionResult() {
        EmptyExecutionResult successResult = new EmptyExecutionResult(true, null);
        assertThat(successResult.executionSuccess()).isTrue();
        assertThat(successResult.message()).isNull();

        EmptyExecutionResult failureResult = new EmptyExecutionResult(false, "something went wrong");
        assertThat(failureResult.executionSuccess()).isFalse();
        assertThat(failureResult.message()).isEqualTo("something went wrong");

        assertThat(EmptyExecutionResult.endExecutionAndGetFinalResult(successResult)).isEqualTo(successResult);
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
