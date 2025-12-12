/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.a2a;

import io.a2a.spec.FilePart;
import io.a2a.spec.FileWithBytes;
import io.a2a.spec.Part;
import org.tarik.ta.UiTestAgent;
import org.tarik.ta.core.a2a.AbstractAgentExecutor;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.dto.UiTestStepResult;

import java.util.List;

import static java.util.Optional.ofNullable;
import static org.tarik.ta.utils.ImageUtils.convertImageToBase64;

public class UiAgentExecutor extends AbstractAgentExecutor {

    @Override
    protected TestExecutionResult executeTestCase(String message) {
        return UiTestAgent.executeTestCase(message);
    }

    @Override
    protected void addSpecificArtifacts(TestExecutionResult result, List<Part<?>> parts) {
        result.stepResults().stream()
                .filter(UiTestStepResult.class::isInstance)
                .map(UiTestStepResult.class::cast)
                .filter(r -> r.screenshot() != null)
                .map(r -> new FileWithBytes(
                        "image/png",
                        "Screenshot for the test step %s".formatted(r.testStep().stepDescription()),
                        convertImageToBase64(r.screenshot(), "png"))
                )
                .map(FilePart::new)
                .forEach(parts::add);

        if (result instanceof UiTestExecutionResult uiResult) {
            ofNullable(uiResult.screenshot()).ifPresent(screenshot ->
                    parts.add(new FilePart(new FileWithBytes("image/png",
                            "General screenshot for the test case %s.png".formatted(result.testCaseName()),
                            convertImageToBase64(screenshot, "png")))));
        }
    }
}