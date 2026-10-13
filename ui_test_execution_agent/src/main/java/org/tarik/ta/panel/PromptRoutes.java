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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.panel.PromptActions.Action;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.javalin.http.HttpStatus.BAD_REQUEST;
import static io.javalin.http.HttpStatus.CONFLICT;
import static io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.javalin.http.HttpStatus.NOT_FOUND;
import static io.javalin.http.HttpStatus.NO_CONTENT;
import static org.tarik.ta.panel.PromptInput.present;

/**
 * The routes of the open prompt: its answer, the hold and the pause of its countdown, its actions and its images. A request for a prompt that is
 * no longer open, or that loses the race with the deadline or another answer, is answered with 409.
 */
@Singleton
final class PromptRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(PromptRoutes.class);
    static final String ANSWER_PATH = "/api/prompts/{id}/answer";
    static final String HOLD_PATH = "/api/prompts/{id}/hold";
    static final String PAUSE_PATH = "/api/prompts/{id}/pause";
    static final String RESUME_PATH = "/api/prompts/{id}/resume";
    static final String ACTION_PATH = "/api/prompts/{id}/actions/{action}";
    static final String IMAGE_PATH = "/api/prompts/{id}/images/{imageId}";
    private static final String CLOSED_MESSAGE = "This prompt is no longer open";
    private static final String BUSY_MESSAGE = "Another action of this prompt is still running";

    private final WebOperatorPanel panel;

    PromptRoutes(WebOperatorPanel panel) {
        this.panel = panel;
    }

    void answer(@NotNull Context context) {
        openPrompt(context).ifPresent(prompt -> answer(context, prompt));
    }

    void hold(@NotNull Context context) {
        openPrompt(context).ifPresent(prompt -> {
            if (panel.hold(prompt)) {
                context.status(NO_CONTENT);
            } else {
                reject(context, CONFLICT, "This prompt has no countdown to hold");
            }
        });
    }

    void pause(@NotNull Context context) {
        openPrompt(context).ifPresent(prompt -> {
            if (panel.pause(prompt)) {
                context.status(NO_CONTENT);
            } else {
                reject(context, CONFLICT, "This prompt has no running countdown to pause");
            }
        });
    }

    void resume(@NotNull Context context) {
        openPrompt(context).ifPresent(prompt -> {
            if (panel.resume(prompt)) {
                context.status(NO_CONTENT);
            } else {
                reject(context, CONFLICT, "This prompt has no paused countdown to resume");
            }
        });
    }

    void action(@NotNull Context context) {
        openPrompt(context).ifPresent(prompt -> {
            var name = context.pathParam("action");
            prompt.spec().actions().find(name).ifPresentOrElse(
                    action -> runAction(context, prompt, name, action),
                    () -> reject(context, NOT_FOUND, "This prompt has no action '%s'".formatted(name)));
        });
    }

    void image(@NotNull Context context) {
        var image = parseId(context).flatMap(panel::pendingPrompt)
                .flatMap(prompt -> prompt.spec().images().find(context.pathParam("imageId")));
        if (image.isEmpty()) {
            context.status(NOT_FOUND);
            return;
        }
        context.contentType(image.get().contentType()).result(image.get().bytes());
    }

    private <A> void answer(Context context, PendingPrompt<A> prompt) {
        var spec = prompt.spec();
        try {
            var answer = present(PanelJson.MAPPER.readValue(context.body(), spec.answerType()), "The answer");
            spec.check().accept(answer);
            if (!prompt.answer().complete(answer)) {
                reject(context, CONFLICT, CLOSED_MESSAGE);
                return;
            }
            LOG.info("Operator prompt {} ({}) answered", prompt.id(), spec.type());
            context.status(NO_CONTENT);
        } catch (JsonProcessingException e) {
            LOG.info("Rejected a malformed answer to operator prompt {} ({}): {}", prompt.id(), spec.type(), e.getOriginalMessage());
            reject(context, BAD_REQUEST, "The answer is malformed");
        } catch (PromptInputException e) {
            reject(context, BAD_REQUEST, e.getMessage());
        }
    }

    private void runAction(Context context, PendingPrompt<?> prompt, String name, Action<?> action) {
        try {
            var request = present(PanelJson.MAPPER.readValue(context.body(), action.requestType()), "The request");
            prompt.runAction(action, request).ifPresentOrElse(
                    result -> context.json(PanelJson.redactedTree(result)),
                    () -> reject(context, CONFLICT, panel.pendingPrompt(prompt.id()).isPresent() ? BUSY_MESSAGE : CLOSED_MESSAGE));
        } catch (JsonProcessingException e) {
            LOG.info("Rejected a malformed '{}' request of operator prompt {}: {}", name, prompt.id(), e.getOriginalMessage());
            reject(context, BAD_REQUEST, "The request is malformed");
        } catch (PromptInputException e) {
            reject(context, BAD_REQUEST, e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("The '{}' action of operator prompt {} failed", name, prompt.id(), e);
            reject(context, INTERNAL_SERVER_ERROR, "The action failed; see the agent log");
        }
    }

    private Optional<PendingPrompt<?>> openPrompt(Context context) {
        var prompt = parseId(context).flatMap(panel::pendingPrompt);
        if (prompt.isEmpty()) {
            reject(context, CONFLICT, CLOSED_MESSAGE);
        }
        return prompt;
    }

    private static Optional<UUID> parseId(Context context) {
        try {
            return Optional.of(UUID.fromString(context.pathParam("id")));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    private static void reject(Context context, HttpStatus status, String message) {
        context.status(status).json(Map.of("error", message));
    }
}
