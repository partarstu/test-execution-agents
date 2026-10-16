/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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
package org.tarik.ta.a2a;

import jakarta.inject.Singleton;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.dto.ItemKind;
import org.tarik.ta.dto.KnowledgeRequest;
import org.tarik.ta.dto.KnowledgeTestCase;
import org.tarik.ta.dto.TestCaseItem;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static org.tarik.ta.knowledge.KnowledgeJson.fromData;

/**
 * Reads the test case of a request from the single data part of its message and validates it against the request limits.
 */
@Singleton
public class KnowledgeRequestReader {
    private final int maxItems;
    private final int maxTextLength;

    public KnowledgeRequestReader(KnowledgeAgentConfig config) {
        this.maxItems = config.getMaxRequestItems();
        this.maxTextLength = config.getMaxRequestTextLength();
    }

    /**
     * Returns the test case of the message with its items numbered in their order.
     *
     * @throws InvalidKnowledgeRequestException when the message carries no data part, several of them, or an invalid test case
     */
    @NotNull
    KnowledgeTestCase read(@Nullable Message message) {
        var request = toRequest(singleDataPart(message));
        var name = requireText(request.name(), "The test case name");
        var items = new ArrayList<TestCaseItem>();
        var preconditions = ofNullable(request.preconditions()).orElse(List.of());
        var steps = ofNullable(request.steps()).orElse(List.of());
        requireItemCount(preconditions.size() + steps.size());
        for (int index = 0; index < preconditions.size(); index++) {
            var text = requireText(preconditions.get(index), "Precondition %d".formatted(index + 1));
            items.add(new TestCaseItem(ItemKind.PRECONDITION, index + 1, text, List.of(), ""));
        }
        for (int index = 0; index < steps.size(); index++) {
            items.add(toStepItem(steps.get(index), index + 1));
        }
        return new KnowledgeTestCase(name, items);
    }

    private static DataPart singleDataPart(@Nullable Message message) {
        var dataParts = ofNullable(message).map(Message::parts).orElse(List.of()).stream()
                .filter(DataPart.class::isInstance)
                .map(DataPart.class::cast)
                .toList();
        if (dataParts.size() != 1) {
            throw new InvalidKnowledgeRequestException(
                    "The message must carry exactly one data part with the test case, but carries %d".formatted(dataParts.size()));
        }
        return dataParts.getFirst();
    }

    private static KnowledgeRequest toRequest(DataPart dataPart) {
        if (dataPart.data() == null) {
            throw new InvalidKnowledgeRequestException("The data part of the message is empty");
        }
        try {
            return fromData(dataPart.data(), KnowledgeRequest.class);
        } catch (IllegalArgumentException e) {
            throw new InvalidKnowledgeRequestException("The data part does not hold a test case: %s".formatted(e.getMessage()));
        }
    }

    private void requireItemCount(int itemCount) {
        if (itemCount == 0) {
            throw new InvalidKnowledgeRequestException("The test case has neither preconditions nor test steps");
        }
        if (itemCount > maxItems) {
            throw new InvalidKnowledgeRequestException(
                    "The test case has %d preconditions and test steps, more than the %d allowed".formatted(itemCount, maxItems));
        }
    }

    private TestCaseItem toStepItem(@Nullable KnowledgeRequest.Step step, int number) {
        var label = "Test step %d".formatted(number);
        if (step == null) {
            throw new InvalidKnowledgeRequestException("%s is missing".formatted(label));
        }
        var action = requireText(step.action(), "The action of %s".formatted(label.toLowerCase()));
        var testData = ofNullable(step.testData()).orElse(List.of()).stream()
                .map(entry -> requireText(entry, "A test data entry of %s".formatted(label.toLowerCase())))
                .toList();
        var expectedResults = requireLength(requireNonNullElse(step.expectedResults(), ""),
                "The expected results of %s".formatted(label.toLowerCase()));
        return new TestCaseItem(ItemKind.STEP, number, action, testData, expectedResults);
    }

    private String requireText(@Nullable String text, String label) {
        if (text == null || text.isBlank()) {
            throw new InvalidKnowledgeRequestException("%s is blank".formatted(label));
        }
        return requireLength(text.strip(), label);
    }

    private String requireLength(String text, String label) {
        if (text.length() > maxTextLength) {
            throw new InvalidKnowledgeRequestException(
                    "%s has %d characters, more than the %d allowed".formatted(label, text.length(), maxTextLength));
        }
        return text;
    }
}
