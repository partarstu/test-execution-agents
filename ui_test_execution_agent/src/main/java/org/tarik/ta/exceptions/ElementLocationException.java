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
package org.tarik.ta.exceptions;

/**
 * Exception thrown when an element cannot be located on the screen.
 * This exception provides detailed information about why the location failed.
 */
public class ElementLocationException extends RuntimeException {
    private final ElementLocationStatus status;

    public ElementLocationException(String locationFailureDescriptionReason, ElementLocationStatus status) {
        super(locationFailureDescriptionReason);
        this.status = status;
    }

    public ElementLocationStatus getStatus() {
        return status;
    }

    public enum ElementLocationStatus {
        NO_ELEMENTS_FOUND_IN_DB,
        SIMILAR_ELEMENTS_IN_DB_BUT_SCORE_TOO_LOW,
        MODEL_COULD_NOT_SELECT_FROM_DB_CANDIDATES,
        ELEMENT_NOT_FOUND_ON_SCREEN_VISUAL_AND_ALGORITHMIC_FAILED,
        ELEMENT_NOT_FOUND_ON_SCREEN_VALIDATION_FAILED,
        UNKNOWN_ERROR
    }
}
