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
package org.tarik.ta.core;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.json.JavalinJackson;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Creates the Javalin instances of the agent servers with the settings they all share.
 */
public final class JavalinAppFactory {
    private static final Logger LOG = LoggerFactory.getLogger(JavalinAppFactory.class);

    private JavalinAppFactory() {
    }

    /** Applies the shared settings first, then the caller's routes and settings; the instance is not started. */
    public static @NotNull Javalin create(@NotNull Consumer<JavalinConfig> configure) {
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson());
            // Guard against the recycled-response failure mode: once a response is recycled, Javalin's default
            // error handling reads from the (also recycled) request and throws again, which it then re-handles
            // recursively in an unbounded loop that floods the log. Handling the IllegalStateException here
            // without touching the recycled context breaks that recursion.
            config.routes.exception(IllegalStateException.class, (exception, context) ->
                    LOG.warn("Suppressing an IllegalStateException from a recycled/committed response to "
                            + "prevent a cascading exception-handler loop.", exception));
            configure.accept(config);
        });
    }
}
