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
