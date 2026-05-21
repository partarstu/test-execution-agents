/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
