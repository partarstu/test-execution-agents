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
package org.tarik.ta.core.model;

import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestStepResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TestExecutionContextTest {

    @Test
    void shouldInitializeCorrectly() {
        TestExecutionContext context = new TestExecutionContext();

        assertThat(context.getTestStepExecutionHistory()).isEmpty();
        assertThat(context.getPreconditionExecutionHistory()).isEmpty();
        assertThat(context.getSharedData()).isEmpty();
    }

    @Test
    void shouldAddStepResult() {
        TestExecutionContext context = new TestExecutionContext();

        TestStepResult result = mock(TestStepResult.class);
        context.addStepResult(result);

        assertThat(context.getTestStepExecutionHistory()).containsExactly(result);
    }

    @Test
    void shouldAddPreconditionResult() {
        TestExecutionContext context = new TestExecutionContext();

        PreconditionResult result = mock(PreconditionResult.class);
        context.addPreconditionResult(result);

        assertThat(context.getPreconditionExecutionHistory()).containsExactly(result);
    }

    @Test
    void shouldAddSharedData() {
        TestExecutionContext context = new TestExecutionContext();

        context.addSharedData("key1", "value1");
        context.addSharedData("key2", 123);

        assertThat(context.getSharedData())
                .containsEntry("key1", "value1")
                .containsEntry("key2", 123);
    }
}
