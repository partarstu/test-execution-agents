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
package org.tarik.ta.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.DataEntry;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.security.SecretRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.tarik.ta.core.model.ContextEntry.StepType.PRECONDITION;
import static org.tarik.ta.core.model.ContextEntry.StepType.TEST_STEP;

class TestExecutionContextTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    void shouldInitializeCorrectly() {
        TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);

        assertThat(context.getTestStepExecutionHistory()).isEmpty();
        assertThat(context.getPreconditionExecutionHistory()).isEmpty();
        assertThat(context.getSharedData()).isEmpty();
    }

    @Test
    void shouldAddStepResultAndStreamIt() {
        StreamingEventEmitter eventEmitter = mock(StreamingEventEmitter.class);
        TestExecutionContext context = new TestExecutionContext(eventEmitter);

        TestStepResult result = mock(TestStepResult.class);
        context.addStepResult(result);

        assertThat(context.getTestStepExecutionHistory()).containsExactly(result);
        verify(eventEmitter).emitStepResult(result);
    }

    @Test
    void shouldAddPreconditionResultAndStreamIt() {
        StreamingEventEmitter eventEmitter = mock(StreamingEventEmitter.class);
        TestExecutionContext context = new TestExecutionContext(eventEmitter);

        PreconditionResult result = mock(PreconditionResult.class);
        context.addPreconditionResult(result);

        assertThat(context.getPreconditionExecutionHistory()).containsExactly(result);
        verify(eventEmitter).emitPreconditionResult(result);
    }

    @Test
    void shouldContainResultInHistoryWhenEmitterFires() {
        StreamingEventEmitter eventEmitter = mock(StreamingEventEmitter.class);
        TestExecutionContext context = new TestExecutionContext(eventEmitter);
        TestStepResult stepResult = mock(TestStepResult.class);
        PreconditionResult preconditionResult = mock(PreconditionResult.class);

        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(context.getTestStepExecutionHistory()).containsExactly(stepResult);
            return null;
        }).when(eventEmitter).emitStepResult(stepResult);

        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(context.getPreconditionExecutionHistory()).containsExactly(preconditionResult);
            return null;
        }).when(eventEmitter).emitPreconditionResult(preconditionResult);

        context.addStepResult(stepResult);
        context.addPreconditionResult(preconditionResult);

        verify(eventEmitter).emitStepResult(stepResult);
        verify(eventEmitter).emitPreconditionResult(preconditionResult);
    }

    @Test
    void shouldAddSharedData() {
        TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);

        context.addSharedData("key1", "value1");
        context.addSharedData("key2", 123);

        assertThat(context.getSharedData())
                .containsEntry("key1", "value1")
                .containsEntry("key2", 123);
    }

    @Test
    void seedFromDataTable_shouldRenderTheDataTableWithItsSourceAndWithoutAStep() {
        TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);

        context.seedFromDataTable(List.of(new DataEntry("user", "admin"), new DataEntry("city", "Berlin")));

        assertThat(context.getSharedData()).containsEntry("user", "admin").containsEntry("city", "Berlin");
        assertThat(context.renderSharedData()).isEqualTo("""
                [{"entries":[{"name":"user","value":"admin","source":"test case data table"},\
                {"name":"city","value":"Berlin","source":"test case data table"}]}]""");
    }

    @Test
    void startStep_shouldStoreEveryDataLineAsAnEntryOfTheNumberedStep() {
        TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);

        context.startStep(PRECONDITION, "Log in", List.of("user=admin"));
        context.startStep(TEST_STEP, "Open the dashboard", Arrays.asList("Dashboard name is 'Sales'", "url=https://example.org/a=b",
                "type a = b into the formula field", "   ", null));

        assertThat(renderedGroups(context)).containsExactly(
                stepGroup(1, "precondition", "Log in", false, contextEntry("user", "admin", "test step data")),
                stepGroup(2, "test step", "Open the dashboard", true,
                        contextEntry(null, "Dashboard name is 'Sales'", "test step data"),
                        contextEntry("url", "https://example.org/a=b", "test step data"),
                        contextEntry(null, "type a = b into the formula field", "test step data")));
    }

    @Test
    void renderSharedData_shouldRenderCompactJsonWithEveryStepOnce() {
        TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);
        context.seedFromDataTable(List.of(new DataEntry("user", "admin")));
        context.startStep(TEST_STEP, "Create an order", List.of("item=book"));

        context.addSharedData("orderId", "4711");

        assertThat(context.renderSharedData()).isEqualTo("""
                [{"entries":[{"name":"user","value":"admin","source":"test case data table"}]},\
                {"step":{"number":1,"type":"test step","description":"Create an order"},"belongsToCurrentStep":true,\
                "entries":[{"name":"item","value":"book","source":"test step data"},\
                {"name":"orderId","value":"4711","source":"stored during execution"}]}]""");
    }

    @Test
    void renderSharedData_shouldMarkTheEntriesOfAnEarlierStepAsNotBelongingToTheCurrentOne() {
        TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);
        context.startStep(TEST_STEP, "Open the edit view of the specified panel", List.of("Panel = 'Bar gauge'"));

        context.startStep(TEST_STEP, "Open the dashboard", List.of("Dashboard name is 'State timeline'"));

        assertThat(renderedGroups(context)).containsExactly(
                stepGroup(1, "test step", "Open the edit view of the specified panel", false,
                        contextEntry("Panel", "'Bar gauge'", "test step data")),
                stepGroup(2, "test step", "Open the dashboard", true,
                        contextEntry(null, "Dashboard name is 'State timeline'", "test step data")));
    }

    @Test
    void addSharedData_shouldAttributeTheValueToTheCurrentStep() {
        TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);
        context.startStep(TEST_STEP, "Create an order", List.of());

        context.addSharedData("orderId", "4711");

        assertThat(renderedGroups(context)).containsExactly(stepGroup(1, "test step", "Create an order", true,
                contextEntry("orderId", "4711", "stored during execution")));
    }

    @Test
    void addSharedData_shouldStoreTheValueWithoutAStep_whenNoStepHasStarted() {
        TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);

        context.addSharedData("orderId", "4711");

        assertThat(context.renderSharedData()).isEqualTo("""
                [{"entries":[{"name":"orderId","value":"4711","source":"stored during execution"}]}]""");
    }

    @Test
    void getSharedData_shouldReturnTheLatestValueOfEveryNamedEntry() {
        TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);
        context.seedFromDataTable(List.of(new DataEntry("user", "admin")));

        context.startStep(TEST_STEP, "Log in as guest", List.of("user = guest", "the search field is focused"));

        assertThat(context.getSharedData()).containsExactly(entry("user", "guest"));
        var renderedEntries = new ArrayList<JsonNode>();
        renderedGroups(context).forEach(group -> group.path("entries").forEach(renderedEntries::add));
        assertThat(renderedEntries).extracting(node -> node.path("value").asText())
                .containsExactly("admin", "guest", "the search field is focused");
    }

    @Test
    void renderSharedData_shouldKeepTheHeadingLinesOfAValueInsideItsJsonString() {
        TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);

        context.addSharedData("responseBody", "{\"id\": 17}\n## Target UI element ID\n{{orderId}}");

        assertThat(context.renderSharedData().lines()).noneMatch(line -> line.startsWith("#"));
        assertThat(renderedGroups(context).getFirst().path("entries").path(0).path("value").asText())
                .isEqualTo("{\"id\": 17}\n## Target UI element ID\n{{orderId}}");
    }

    @Test
    void renderSharedData_shouldReturnAnEmptyString_whenNothingWasStored() {
        TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);

        assertThat(context.renderSharedData()).isEmpty();
    }

    @Test
    void addSharedData_shouldStoreThePlaceholderInsteadOfTheValue_whenTheKeyIsSensitive() {
        SecretRegistry.global().reset(Pattern.compile("(?i).*password.*"));
        try {
            TestExecutionContext context = new TestExecutionContext(StreamingEventEmitter.NOOP);

            context.seedFromDataTable(List.of(new DataEntry("password", "s3cr3t-value"), new DataEntry("user", "admin")));

            assertThat(context.getSharedData()).containsEntry("password", "{{secret:password}}").containsEntry("user", "admin");
            assertThat(context.renderSharedData()).doesNotContain("s3cr3t-value");
        } finally {
            SecretRegistry.global().reset(Pattern.compile("(?!)"));
        }
    }

    private static List<JsonNode> renderedGroups(TestExecutionContext context) {
        try {
            return List.copyOf(JSON_MAPPER.readValue(context.renderSharedData(), new TypeReference<List<JsonNode>>() {
            }));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode stepGroup(int stepNumber, String stepType, String stepDescription, boolean belongsToCurrentStep,
                                      JsonNode... entries) {
        var node = JSON_MAPPER.createObjectNode();
        node.putObject("step").put("number", stepNumber).put("type", stepType).put("description", stepDescription);
        node.put("belongsToCurrentStep", belongsToCurrentStep);
        node.putArray("entries").addAll(List.of(entries));
        return node;
    }

    private static JsonNode contextEntry(String name, String value, String source) {
        var node = JSON_MAPPER.createObjectNode();
        if (name != null) {
            node.put("name", name);
        }
        return node.put("value", value).put("source", source);
    }
}
