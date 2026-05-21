/*
 * Test Execution Agent Parent - Parent build/dependency management for the Test Execution Agents system.
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
