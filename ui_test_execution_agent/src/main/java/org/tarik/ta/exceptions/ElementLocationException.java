/*
 * ui-test-execution-agent - ${project.description}
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
