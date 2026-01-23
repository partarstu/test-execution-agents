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

import dev.langchain4j.model.output.structured.Description;

/**
 * Result of the user confirmation for selecting a UI element in semi-attended mode.
 * The user can choose to proceed with the selected element, create a new one, or perform another action.
 */
@Description("Result of the user confirmation for selecting a UI element")
public record SemiAttendedModeElementLocationConfirmationResult(
        @Description("The user's decision on how to proceed") ConfirmationDecision decision,
        @Description("Message or details about the decision") String message
) {
    public enum ConfirmationDecision {
        PROCEED,
        CREATE_NEW_ELEMENT,
        OTHER_ACTION
    }

    public static SemiAttendedModeElementLocationConfirmationResult proceed() {
        return new SemiAttendedModeElementLocationConfirmationResult(ConfirmationDecision.PROCEED, "User chose to proceed with the selected element");
    }

    public static SemiAttendedModeElementLocationConfirmationResult createNewElement() {
        return new SemiAttendedModeElementLocationConfirmationResult(ConfirmationDecision.CREATE_NEW_ELEMENT, "User chose to create a new element");
    }

    public static SemiAttendedModeElementLocationConfirmationResult otherAction() {
        return new SemiAttendedModeElementLocationConfirmationResult(ConfirmationDecision.OTHER_ACTION, "User chose to perform another action");
    }
}
