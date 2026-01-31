package org.tarik.ta.core.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MyClassTest {

    @Mock
    private DependencyClass mockDependency;

    private MyClass underTest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        underTest = new MyClass(mockDependency);
    }

    @Test
    void methodName_shouldDoSomething_whenCondition() {
        // Given
        when(mockDependency.getData()).thenReturn("test-data");

        // When
        String result = underTest.processData();

        // Then
        assertThat(result).isEqualTo("processed: test-data");
        verify(mockDependency).getData();
    }
}
