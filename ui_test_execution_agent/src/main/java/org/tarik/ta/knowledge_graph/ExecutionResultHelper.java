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
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.FAILURE;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;

final class ExecutionResultHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionResultHelper.class);

    private ExecutionResultHelper() {}

    static UiTestStepResult mergeAtomicResults(TestStep testStep, List<UiTestStepResult> results) {
        if (results.isEmpty()) {
            var errorMessage = "Execution of test step '%s' was aborted.".formatted(testStep.stepDescription());
            LOG.error(errorMessage);
            return new UiTestStepResult(testStep, FAILURE, errorMessage, "No atomic steps executed", null, now(), now());
        }

        boolean allSuccess = results.stream().allMatch(r -> r.getExecutionStatus() == SUCCESS);
        var finalStatus = allSuccess ? SUCCESS : results.getLast().getExecutionStatus();
        var finalError = results.stream()
                .map(TestStepResult::getErrorMessage)
                .filter(CommonUtils::isNotBlank)
                .collect(joining("\n"))
                .trim();
        if (isBlank(finalError) && !allSuccess) {
            finalError = "Execution of test step '%s' was aborted.".formatted(testStep.stepDescription());
        }
        var finalActualResult = results.stream()
                .map(TestStepResult::getActualResult)
                .filter(Objects::nonNull)
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
