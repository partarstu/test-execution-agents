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
package org.tarik.ta.core.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.core.manager.BudgetManager;

import java.lang.reflect.Method;
import java.util.*;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;

/**
 * A custom ToolProvider that scans all methods (including inherited ones) for @Tool annotations.
 * <p>
 * LangChain4j's default tool scanning uses {@code getDeclaredMethods()} which only returns methods
 * declared directly in the class, not inherited methods. This provider uses {@code getMethods()}
 * to include inherited @Tool methods from parent classes.
 */
public class InheritanceAwareToolProvider<T extends FinalResult> implements ToolProvider {
    private final List<Object> toolObjects;
    private final List<Object> resultToolObjects;
    private final Class<T> resultClass;

    /**
     * Creates an InheritanceAwareToolProvider that scans the provided tool objects and adds the final result tool.
     *
     * @param toolObjects Collection of objects containing @Tool annotated methods (including inherited ones)
     * @param resultClass The class of the final result, which may also contain static @Tool methods. Must not be null.
     */
    public InheritanceAwareToolProvider(@NotNull Collection<?> toolObjects, @NotNull Class<T> resultClass) {
        this(toolObjects, List.of(), resultClass);
    }

    /**
     * Creates an InheritanceAwareToolProvider whose final result is returned by the @Tool methods of {@code resultToolObjects} (e.g. a
     * result tool that validates the result against the state of the run) instead of static methods of the result class.
     */
    public InheritanceAwareToolProvider(@NotNull Collection<?> toolObjects, @NotNull Collection<?> resultToolObjects,
                                        @NotNull Class<T> resultClass) {
        this.toolObjects = new ArrayList<>(toolObjects);
        this.resultToolObjects = new ArrayList<>(resultToolObjects);
        this.resultClass = Objects.requireNonNull(resultClass, "resultClass must not be null");
    }

    /**
     * Creates an InheritanceAwareToolProvider that scans the provided tool objects.
     *
     * @param resultClass The class of the final result, which may also contain static @Tool methods. Must not be null.
     */
    public InheritanceAwareToolProvider(@NotNull Class<T> resultClass) {
        this(List.of(), List.of(), resultClass);
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        var resultBuilder = ToolProviderResult.builder();
        toolObjects.forEach(toolObject -> Arrays.stream(toolObject.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .forEach(toolMethod -> addToolProvider(toolObject, toolMethod, resultBuilder, true)));
        resultToolObjects.forEach(toolObject -> Arrays.stream(toolObject.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .forEach(toolMethod -> addToolProvider(toolObject, toolMethod, resultBuilder, false)));
        Arrays.stream(resultClass.getMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .forEach(toolMethod -> addToolProvider(resultClass, toolMethod, resultBuilder, false));
        return resultBuilder.build();
    }

    private static void addToolProvider(Object toolObject, Method toolMethod, ToolProviderResult.Builder resultBuilder,
                                        boolean checksRunBudgets) {
        ToolSpecification specification = toolSpecificationFrom(toolMethod);
        ToolExecutor executor = DefaultToolExecutor.builder()
                .object(toolObject)
                .originalMethod(toolMethod)
                .methodToInvoke(toolMethod)
                .wrapToolArgumentsExceptions(true)
                .propagateToolExecutionExceptions(true)
                .build();
        resultBuilder.add(specification, withToolCallBudgetTracking(executor, checksRunBudgets),
                toolMethod.getAnnotation(Tool.class).returnBehavior());
    }

    /**
     * Counts every tool execution against the tool-call budget and, if requested, checks the run-wide time and token budgets before
     * it runs, so that a long agent invocation stops when either is exhausted instead of only before the next one.
     */
    private static ToolExecutor withToolCallBudgetTracking(ToolExecutor delegate, boolean checksRunBudgets) {
        // Both entry points are overridden: overriding only execute would lose the delegate's executeWithContext behavior (the
        // propagation of the tool result object, which the agents' result extraction relies on).
        return new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                consumeToolCall(checksRunBudgets);
                return delegate.execute(request, memoryId);
            }

            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
                consumeToolCall(checksRunBudgets);
                return delegate.executeWithContext(request, context);
            }
        };
    }

    // The tool-call budget is a budget per step, which the agents check before each invocation; within one invocation the
    // model's tool-calling round trips are bounded by the agent framework instead. The final-result tools skip the run-wide checks,
    // so that a result the model has already produced is kept instead of being discarded by a budget exhausted on its last turn.
    private static void consumeToolCall(boolean checksRunBudgets) {
        var budgetManager = BudgetManager.getInstance();
        budgetManager.consumeToolCalls(1);
        if (checksRunBudgets) {
            budgetManager.checkTimeBudget();
            budgetManager.checkTokenBudget();
        }
    }
}