package org.tarik.ta.core.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;

class TestExecutionResultTest {

        @Test
        void shouldInitializeCorrectly() {
                String testCaseName = "Test Case 1";
                TestExecutionResult.TestExecutionStatus status = PASSED;
                List<PreconditionResult> preconditions = Collections.emptyList();
                List<TestStepResult> steps = Collections.emptyList();
                Instant start = Instant.now();
                Instant end = start.plusSeconds(1);
                String error = null;

                TestExecutionResult result = new TestExecutionResult(testCaseName, status, preconditions, steps, start,
                                end,
                                error, null, null);

                assertThat(result.getTestCaseName()).isEqualTo(testCaseName);
                assertThat(result.getTestExecutionStatus()).isEqualTo(status);
                assertThat(result.getPreconditionResults()).isEqualTo(preconditions);
                assertThat(result.getStepResults()).isEqualTo(steps);
                assertThat(result.getExecutionStartTimestamp()).isEqualTo(start);
                assertThat(result.getExecutionEndTimestamp()).isEqualTo(end);
                assertThat(result.getGeneralErrorMessage()).isNull();
        }

        @Test
        void testEqualsAndHashCode() {
                Instant now = Instant.now();
                TestExecutionResult result1 = new TestExecutionResult("TC1", PASSED, Collections.emptyList(),
                                Collections.emptyList(), now, now, null, null, null);
                TestExecutionResult result2 = new TestExecutionResult("TC1", PASSED, Collections.emptyList(),
                                Collections.emptyList(), now, now, null, null, null);
                TestExecutionResult result3 = new TestExecutionResult("TC2", FAILED, Collections.emptyList(),
                                Collections.emptyList(), now, now, "Error", null, null);

                assertThat(result1).isEqualTo(result2);
                assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
                assertThat(result1).isNotEqualTo(result3);
        }

        @Test
        void testToString_WithStepsAndPreconditions() {
                PreconditionResult precondition = new PreconditionResult("Precond 1", true, null, Instant.now(),
                                Instant.now());
                TestStep step = new TestStep("Action", Collections.emptyList(), "Verification");
                TestStepResult stepResult = new TestStepResult(step, TestStepResult.TestStepResultStatus.SUCCESS, null,
                                null,
                                Instant.now(), Instant.now());

                TestExecutionResult result = new TestExecutionResult(
                                "Complex Test",
                                PASSED,
                                List.of(precondition),
                                List.of(stepResult),
                                Instant.now(),
                                Instant.now(),
                                null,
                                null,
                                null);

                String str = result.toString();
                assertThat(str).contains("Test Case: Complex Test");
                assertThat(str).contains("Execution Result: PASSED");
                assertThat(str).contains("Preconditions:");
                assertThat(str).contains("Precond 1");
                assertThat(str).contains("Steps:");
                assertThat(str).contains("Action");
        }

        @Test
        void testToString_Failed() {
                TestExecutionResult result = new TestExecutionResult(
                                "Failed Test",
                                FAILED,
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Instant.now(),
                                Instant.now(),
                                "Something went wrong",
                                null,
                                null);

                String str = result.toString();
                assertThat(str).contains("Test Case: Failed Test");
                assertThat(str).contains("Execution Result: FAILED");
                assertThat(str).contains("Error Message: Something went wrong");
                assertThat(str).contains("No steps were executed");
        }
}
