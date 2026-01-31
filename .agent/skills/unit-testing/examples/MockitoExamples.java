package org.tarik.ta.examples;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.MockitoAnnotations;

class MockitoExamples {

    @Mock
    private MyDependency mock;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void stubbingBehavior() {
        // Return value
        when(mock.getValue()).thenReturn("result");

        // Return different values on consecutive calls
        when(mock.getValue())
                .thenReturn("first")
                .thenReturn("second");

        // Throw exception
        when(mock.riskyMethod()).thenThrow(new RuntimeException("error"));

        // With argument matchers
        when(mock.process(any())).thenReturn("processed");
        when(mock.process(eq("specific"))).thenReturn("specific-result");
        when(mock.process(anyString())).thenReturn("string-result");
    }

    @Test
    void verifyingInteractions() {
        mock.methodName("arg1", "arg2");

        // Verify method called
        verify(mock).methodName(any(), any());

        // Verify with arguments
        verify(mock).methodName("arg1", "arg2");

        // Verify call count
        verify(mock, times(1)).methodName(any(), any());
        verify(mock, never()).otherMethod();
    }

    @Test
    void argumentCaptors() {
        mock.methodName("expected");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mock).methodName(captor.capture());
        assertThat(captor.getValue()).isEqualTo("expected");
    }
}
