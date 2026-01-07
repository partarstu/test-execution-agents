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
package org.tarik.ta.dto;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Represents the status of a verification operation.
 *
 * @param timedOut Whether the wait for verification timed out before the verification completed.
 *                 When true, success will be null since the verification result is not yet available.
 * @param success  The result of the verification. Null if verification hasn't completed yet (timedOut=true),
 *                 Boolean.TRUE if verification passed, Boolean.FALSE if verification failed.
 */
public record VerificationStatus(boolean timedOut, @Nullable Boolean success) {

    /**
     * Returns true if the verification completed (regardless of success/failure).
     */
    public boolean isCompleted() {
        return !timedOut && success != null;
    }

    /**
     * Returns Optional containing verification success result.
     */
    public Optional<Boolean> isSuccessful() {
        return isCompleted() ? Optional.of(success) : Optional.empty();
    }
}
