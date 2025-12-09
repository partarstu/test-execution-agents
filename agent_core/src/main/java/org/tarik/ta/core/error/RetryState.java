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
