/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
package org.tarik.ta.panel;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Checks of the answers and action requests a browser sends, each throwing {@link PromptInputException} on invalid input. */
final class PromptInput {
    static final int MAX_TEXT_LENGTH = 5000;
    static final int MAX_QUERY_LENGTH = 500;
    static final int MAX_LIST_SIZE = 200;

    private PromptInput() {
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new PromptInputException(message);
        }
    }

    static <T> T present(@Nullable T value, String name) {
        require(value != null, "%s is missing".formatted(name));
        return value;
    }

    static String text(@Nullable String value, String name) {
        return text(value, MAX_TEXT_LENGTH, name);
    }

    static String text(@Nullable String value, int maxLength, String name) {
        var present = present(value, name);
        require(present.length() <= maxLength, "%s is longer than %d characters".formatted(name, maxLength));
        return present;
    }

    static List<String> texts(@Nullable List<String> values, String name) {
        list(values, name).forEach(value -> text(value, name));
        return values;
    }

    static <T extends Collection<?>> T list(@Nullable T values, String name) {
        var present = present(values, name);
        require(present.size() <= MAX_LIST_SIZE, "%s has more than %d entries".formatted(name, MAX_LIST_SIZE));
        require(present.stream().allMatch(Objects::nonNull), "%s has an empty entry".formatted(name));
        return present;
    }
}
