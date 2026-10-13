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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.avaje.inject.External;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.DataEntry;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.config.scopes.BaseAgentRequestScope;
import org.tarik.ta.core.model.ContextEntry.Provenance;
import org.tarik.ta.core.model.ContextEntry.Step;
import org.tarik.ta.core.model.ContextEntry.StepType;
import org.tarik.ta.core.security.SecretRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static org.tarik.ta.core.model.ContextEntry.Provenance.DATA_TABLE;
import static org.tarik.ta.core.model.ContextEntry.Provenance.TEST_STEP;
import static org.tarik.ta.core.model.ContextEntry.Provenance.TOOL;

/**
 * Holds the context and state of the current test execution.
 */
@BaseAgentRequestScope
public class TestExecutionContext {
    /** The heading of the prompt section holding {@link #renderSharedData()}, the term the agents' system prompts use for it. */
    public static final String EXECUTION_CONTEXT_DATA_LABEL = "Test execution context data";
    /** The heading of the prompt section holding the data a precondition was given, the term the agents' system prompts use for it. */
    public static final String PRECONDITION_DATA_LABEL = "Data relevant to this precondition";
    /**
     * A data line names a context entry only when everything before the first {@code =} is a single word, so that a sentence
     * which happens to contain an equals sign ("type a=b into the field") is not mistaken for one.
     */
    private static final Pattern NAMED_ENTRY = Pattern.compile("^([^\\s=]+)\\s*=\\s*(.*)$", Pattern.DOTALL);
    private static final ObjectWriter JSON_WRITER = new ObjectMapper().writer();

    private final List<TestStepResult> testStepExecutionHistory;
    private final List<PreconditionResult> preconditionExecutionHistory;
    private final List<ContextEntry> sharedData;
    private final StreamingEventEmitter eventEmitter;
    private final ReentrantLock lock = new ReentrantLock();
    private @Nullable Step currentStep;

    public TestExecutionContext(@External @NotNull StreamingEventEmitter eventEmitter) {
        this.eventEmitter = eventEmitter;
        this.testStepExecutionHistory = new ArrayList<>();
        this.preconditionExecutionHistory = new ArrayList<>();
        this.sharedData = new ArrayList<>();
    }

    public @NotNull StreamingEventEmitter getEventEmitter() {
        return eventEmitter;
    }

    public List<TestStepResult> getTestStepExecutionHistory() {
        lock.lock();
        try {
            return new ArrayList<>(testStepExecutionHistory);
        } finally {
            lock.unlock();
        }
    }

    public List<PreconditionResult> getPreconditionExecutionHistory() {
        lock.lock();
        try {
            return new ArrayList<>(preconditionExecutionHistory);
        } finally {
            lock.unlock();
        }
    }

    /**
     * The latest value of every named context entry, for tools that need the value itself; prompts use
     * {@link #renderSharedData()} instead.
     */
    public Map<String, Object> getSharedData() {
        var values = new LinkedHashMap<String, Object>();
        snapshotSharedData().stream()
                .filter(entry -> entry.name() != null)
                .forEach(entry -> values.put(entry.name(), entry.value()));
        return values;
    }

    /**
     * Renders the context entries for a prompt as compact JSON grouped per precondition or test step in execution order, or returns
     * an empty string when nothing is stored.
     */
    public String renderSharedData() {
        Step current;
        List<ContextEntry> entries;
        lock.lock();
        try {
            current = currentStep;
            entries = snapshotSharedData();
        } finally {
            lock.unlock();
        }
        if (entries.isEmpty()) {
            return "";
        }
        try {
            return JSON_WRITER.writeValueAsString(groupByStep(entries, current));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not render the test execution context data as JSON", e);
        }
    }

    public void addStepResult(TestStepResult result) {
        lock.lock();
        try {
            this.testStepExecutionHistory.add(result);
        } finally {
            lock.unlock();
        }
        eventEmitter.emitStepResult(result);
    }

    public void addPreconditionResult(PreconditionResult result) {
        lock.lock();
        try {
            this.preconditionExecutionHistory.add(result);
        } finally {
            lock.unlock();
        }
        eventEmitter.emitPreconditionResult(result);
    }

    /**
     * Seeds the context with the data table of the incoming test case, so that every precondition and test step sees it,
     * including a run that starts from a later step.
     */
    public void seedFromDataTable(@NotNull List<DataEntry> dataTable) {
        dataTable.forEach(entry -> addEntry(entry.key(), entry.value(), DATA_TABLE, null));
    }

    /**
     * Makes the precondition or test step which is about to run the current one, numbering it in execution order, and stores
     * every non-blank line of its data as an entry of that step, named by the key of a {@code key=value} line.
     */
    public void startStep(@NotNull StepType type, @NotNull String description, @NotNull List<String> dataLines) {
        Step step;
        lock.lock();
        try {
            step = new Step(currentStep == null ? 1 : currentStep.number() + 1, type, description);
            currentStep = step;
        } finally {
            lock.unlock();
        }
        dataLines.stream()
                .filter(line -> line != null && !line.isBlank())
                .map(String::trim)
                .forEach(line -> addDataLine(line, step));
    }

    /** Stores a value a tool call produced during the current precondition or test step. */
    public void addSharedData(String key, Object value) {
        Step step;
        lock.lock();
        try {
            step = currentStep;
        } finally {
            lock.unlock();
        }
        addEntry(key, value, TOOL, step);
    }

    private void addDataLine(@NotNull String line, @NotNull Step step) {
        var matcher = NAMED_ENTRY.matcher(line);
        if (matcher.matches()) {
            addEntry(matcher.group(1), matcher.group(2).trim(), TEST_STEP, step);
        } else {
            addEntry(null, line, TEST_STEP, step);
        }
    }

    private void addEntry(@Nullable String name, @Nullable Object value, @NotNull Provenance source, @Nullable Step step) {
        // A value under a sensitive key never enters the context itself: what is stored, rendered into prompts and
        // written into artifacts is the placeholder, and only the consuming tool resolves it back.
        Object storedValue = name == null ? value : SecretRegistry.global().register(name, value).map(Object.class::cast).orElse(value);
        lock.lock();
        try {
            this.sharedData.add(new ContextEntry(name, storedValue, source, step));
        } finally {
            lock.unlock();
        }
    }

    private List<ContextEntry> snapshotSharedData() {
        lock.lock();
        try {
            return List.copyOf(sharedData);
        } finally {
            lock.unlock();
        }
    }

    private static List<RenderedGroup> groupByStep(@NotNull List<ContextEntry> entries, @Nullable Step currentStep) {
        // The entries of the whole test case have no step, so they are grouped under the null key
        var entriesByStep = new LinkedHashMap<Step, List<RenderedEntry>>();
        entries.forEach(entry -> entriesByStep.computeIfAbsent(entry.step(), _ -> new ArrayList<>())
                .add(new RenderedEntry(entry.name(), entry.value(), entry.source())));
        return entriesByStep.entrySet().stream()
                .map(group -> RenderedGroup.of(group.getKey(), currentStep, group.getValue()))
                .toList();
    }

    @JsonInclude(NON_NULL)
    record RenderedGroup(@Nullable Step step, @Nullable Boolean belongsToCurrentStep, @NotNull List<RenderedEntry> entries) {
        static RenderedGroup of(@Nullable Step step, @Nullable Step currentStep, @NotNull List<RenderedEntry> entries) {
            var belongsToCurrentStep = step == null ? null : Objects.equals(step, currentStep);
            return new RenderedGroup(step, belongsToCurrentStep, List.copyOf(entries));
        }
    }

    @JsonInclude(NON_NULL)
    record RenderedEntry(@Nullable String name, @Nullable Object value, @NotNull Provenance source) {
    }
}
