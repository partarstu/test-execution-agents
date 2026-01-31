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
