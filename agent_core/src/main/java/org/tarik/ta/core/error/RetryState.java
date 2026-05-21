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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RetryState {
    private final AtomicInteger attempts = new AtomicInteger(0);
    private final AtomicLong startTime = new AtomicLong(0);

    public void reset() {
        attempts.set(0);
        startTime.set(0);
    }

    public int incrementAttempts() {
        return attempts.incrementAndGet();
    }

    public void startIfNotStarted() {
        startTime.compareAndSet(0, System.currentTimeMillis());
    }

    public long getElapsedTime() {
        long start = startTime.get();
        return start == 0 ? 0 : System.currentTimeMillis() - start;
    }

    public int getAttempts() {
        return attempts.get();
    }
}
