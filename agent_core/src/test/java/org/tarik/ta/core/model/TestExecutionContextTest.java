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
