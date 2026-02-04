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
package org.tarik.ta.examples;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

class AssertionExamples {

    @Test
    void stringAssertions() {
        String result = "expected";
        assertThat(result).isEqualTo("expected");
        assertThat(result).contains("pect");
        assertThat(result).startsWith("exp");
        assertThat(result).isNotBlank();
    }

    @Test
    void collectionAssertions() {
        List<String> list = List.of("a", "b", "c");
        assertThat(list).isEmpty(); // Will fail, illustrating usage
        assertThat(list).isNotEmpty();
        assertThat(list).hasSize(3);
        assertThat(list).contains("a", "b");
        assertThat(list).containsExactly("a", "b", "c");
        assertThat(list).containsExactlyInAnyOrder("c", "a", "b");

        Map<String, String> map = Map.of("key", "value");
        assertThat(map).containsKey("key");
        assertThat(map).containsEntry("key", "value");
    }

    @Test
    void exceptionAssertions() {
         // Assert exception is thrown
        assertThatThrownBy(() -> { throw new RuntimeException("error message"); })
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("error message");

        // Assert no exception
        assertThatCode(() -> { /* safe code */ })
                .doesNotThrowAnyException();
    }
}
