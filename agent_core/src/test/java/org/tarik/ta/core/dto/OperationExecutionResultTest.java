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

import static org.assertj.core.api.Assertions.assertThat;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.*;

class OperationExecutionResultTest {

    @Test
    void testAccessors() {
        OperationExecutionResult<String> result = new OperationExecutionResult<>(SUCCESS, "msg", "payload");
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.getMessage()).isEqualTo("msg");
        assertThat(result.getResultPayload()).isEqualTo("payload");
        
        OperationExecutionResult<String> error = new OperationExecutionResult<>(ERROR, "error");
        assertThat(error.isSuccess()).isFalse();
        assertThat(error.getExecutionStatus()).isEqualTo(ERROR);
        assertThat(error.getMessage()).isEqualTo("error");
        assertThat(error.getResultPayload()).isNull();
    }

    @Test
    void testEqualsAndHashCode() {
        OperationExecutionResult<Integer> r1 = new OperationExecutionResult<>(SUCCESS, "ok", 123);
        OperationExecutionResult<Integer> r2 = new OperationExecutionResult<>(SUCCESS, "ok", 123);
        OperationExecutionResult<Integer> r3 = new OperationExecutionResult<>(INTERRUPTED_BY_USER, "stop", null);

        assertThat(r1).isEqualTo(r2);
        assertThat(r1).hasSameHashCodeAs(r2);
        assertThat(r1).isNotEqualTo(r3);
        assertThat(r1).isNotEqualTo(null);
    }

    @Test
    void testToString() {
        OperationExecutionResult<String> result = new OperationExecutionResult<>(SUCCESS, "m", "p");
        assertThat(result.toString()).contains("OperationExecutionResult", "SUCCESS", "m", "p");
    }
}
