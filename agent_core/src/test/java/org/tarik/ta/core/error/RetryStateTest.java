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
package org.tarik.ta.core.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryStateTest {

    @Test
    void shouldTrackAttempts() {
        RetryState state = new RetryState();
        assertThat(state.getAttempts()).isEqualTo(0);

        assertThat(state.incrementAttempts()).isEqualTo(1);
        assertThat(state.getAttempts()).isEqualTo(1);

        assertThat(state.incrementAttempts()).isEqualTo(2);
        assertThat(state.getAttempts()).isEqualTo(2);
    }

    @Test
    void shouldTrackElapsedTime() throws InterruptedException {
        RetryState state = new RetryState();
        assertThat(state.getElapsedTime()).isEqualTo(0);

        state.startIfNotStarted();
        Thread.sleep(50);
        
        long elapsed = state.getElapsedTime();
        assertThat(elapsed).isGreaterThanOrEqualTo(50);
        
        // startIfNotStarted should not reset the time if already started
        state.startIfNotStarted();
        Thread.sleep(50);
        
        long elapsed2 = state.getElapsedTime();
        assertThat(elapsed2).isGreaterThanOrEqualTo(100);
    }

    @Test
    void reset_shouldClearState() {
        RetryState state = new RetryState();
        state.startIfNotStarted();
        state.incrementAttempts();

        state.reset();

        assertThat(state.getAttempts()).isEqualTo(0);
        assertThat(state.getElapsedTime()).isEqualTo(0);
    }
}
