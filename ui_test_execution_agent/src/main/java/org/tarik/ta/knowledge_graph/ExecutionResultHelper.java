/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
package org.tarik.ta.knowledge_graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.dto.UiPreconditionResult;
import org.tarik.ta.dto.UiTestStepResult;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static java.time.Instant.now;
import static java.util.stream.Collectors.joining;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.ERROR;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.FAILURE;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.core.utils.CommonUtils.isNotBlank;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;

final class ExecutionResultHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionResultHelper.class);

    private ExecutionResultHelper() {
    }

    static UiTestStepResult mergeAtomicResults(TestStep testStep, List<UiTestStepResult> results) {
        if (results.isEmpty()) {
            var errorMessage = "Execution of test step '%s' was aborted.".formatted(testStep.stepDescription());
            LOG.error(errorMessage);
            // An abort is not a failed verification, so it surfaces as ERROR per the unified status contract.
            return new UiTestStepResult(testStep, ERROR, errorMessage, "No atomic steps executed", null, now(), now());
        }

        boolean allSuccess = results.stream().allMatch(r -> r.getExecutionStatus() == SUCCESS);
        var finalError = results.stream()
                .map(UiTestStepResult::getErrorMessage)
                .filter(CommonUtils::isNotBlank)
                .collect(joining("\n"))
                .trim();
        if (isBlank(finalError) && !allSuccess) {
            finalError = "Execution of test step '%s' was aborted.".formatted(testStep.stepDescription());
        }
        if (allSuccess && isNotBlank(finalError)) {
            LOG.error("Got a situation when procedure results are OK, but one of them has error message. It means there's a bug in the " +
                    "corresponding code. The steps affected:\n <{}>", results);
        }
        // ERROR must survive the merge: FAILURE is reserved for failed verifications, while any executor error makes
        // the whole step (and thus the test case) an ERROR.
        boolean anyError = results.stream().anyMatch(r -> r.getExecutionStatus() == ERROR);
        var finalStatus = isBlank(finalError) ? SUCCESS : anyError ? ERROR : FAILURE;
        var finalActualResult = results.stream()
                .map(TestStepResult::getActualResult)
                .filter(Objects::nonNull)
                .filter(r -> !r.equals(StepExecutionOrchestrator.NO_VERIFICATION_REQUIRED))
                .collect(joining("\n"));
        Instant start = results.getFirst().getExecutionStartTimestamp();
        Instant end = results.getLast().getExecutionEndTimestamp();
        BufferedImage screenshot = results.getLast().getScreenshot();
        return new UiTestStepResult(testStep, finalStatus, finalError, finalActualResult, screenshot, start, end);
    }

    static UiPreconditionResult mergePreconditionResults(String preconditionDescription, List<UiPreconditionResult> results) {
        if (results.isEmpty()) {
            var errorMessage = "Execution of precondition '%s' was aborted.".formatted(preconditionDescription);
            LOG.error(errorMessage);
            return new UiPreconditionResult(preconditionDescription, false, errorMessage, captureScreen(), now(), now());
        }
        var start = results.getFirst().getExecutionStartTimestamp();
        var end = results.getLast().getExecutionEndTimestamp();
        var failedResult = results.stream().filter(r -> !r.isSuccess()).findFirst();
        return failedResult.map(r ->
                        new UiPreconditionResult(preconditionDescription, false, r.getErrorMessage(), r.getScreenshot(), start, end))
                .orElseGet(() -> new UiPreconditionResult(preconditionDescription, true, null, null, start, end));
    }
}
