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

import java.util.UUID;

public record ElementRefinementOperation(Operation operation, UUID elementId) {
    public enum Operation {
        UPDATE_SCREENSHOT,
        UPDATE_ELEMENT,
        DELETE_ELEMENT,
        DONE
    }

    public static ElementRefinementOperation forUpdateScreenshot(UUID elementId) {
        return new ElementRefinementOperation(Operation.UPDATE_SCREENSHOT, elementId);
    }

    public static ElementRefinementOperation forUpdateElement(UUID elementId) {
        return new ElementRefinementOperation(Operation.UPDATE_ELEMENT, elementId);
    }

    public static ElementRefinementOperation forDeleteElement(UUID elementId) {
        return new ElementRefinementOperation(Operation.DELETE_ELEMENT, elementId);
    }

    public static ElementRefinementOperation done() {
        return new ElementRefinementOperation(Operation.DONE, null);
    }
}
