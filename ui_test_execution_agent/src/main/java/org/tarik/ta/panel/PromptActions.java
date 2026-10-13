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

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** The actions the browser may invoke while one prompt is open, by name, each with the request record it accepts. */
final class PromptActions {
    static final PromptActions NONE = new PromptActions(Map.of());

    private final Map<String, Action<?>> actions;

    private PromptActions(Map<String, Action<?>> actions) {
        this.actions = Map.copyOf(actions);
    }

    /** An action whose handler validates the request and returns what is sent back; one that drives the display is awaited on close. */
    record Action<R>(Class<R> requestType, boolean drivesDisplay, Function<R, ?> handler) {
        Object run(Object request) {
            return handler.apply(requestType.cast(request));
        }
    }

    static Builder builder() {
        return new Builder();
    }

    @NotNull Optional<Action<?>> find(@NotNull String name) {
        return Optional.ofNullable(actions.get(name));
    }

    static final class Builder {
        private final Map<String, Action<?>> actions = new HashMap<>();

        <R> Builder add(String name, Class<R> requestType, Function<R, ?> handler) {
            actions.put(name, new Action<>(requestType, false, handler));
            return this;
        }

        <R> Builder addDrivingDisplay(String name, Class<R> requestType, Function<R, ?> handler) {
            actions.put(name, new Action<>(requestType, true, handler));
            return this;
        }

        PromptActions build() {
            return new PromptActions(actions);
        }
    }
}
