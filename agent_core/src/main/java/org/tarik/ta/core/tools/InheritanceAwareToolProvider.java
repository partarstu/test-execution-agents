/*
 * agent-core - ${project.description}
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
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.core.dto.FinalResult;

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
    private final Class<T> resultClass;

    /**
     * Creates an InheritanceAwareToolProvider that scans the provided tool objects and adds the final result tool.
     *
     * @param toolObjects Collection of objects containing @Tool annotated methods (including inherited ones)
     * @param resultClass The class of the final result, which may also contain static @Tool methods. Must not be null.
     */
    public InheritanceAwareToolProvider(@NotNull Collection<?> toolObjects, @NotNull Class<T> resultClass) {
        this.toolObjects = new ArrayList<>(toolObjects);
        this.resultClass = Objects.requireNonNull(resultClass, "resultClass must not be null");
    }

    /**
     * Creates an InheritanceAwareToolProvider that scans the provided tool objects.
     *
     * @param resultClass The class of the final result, which may also contain static @Tool methods. Must not be null.
     */
    public InheritanceAwareToolProvider(@NotNull Class<T> resultClass) {
        this.toolObjects = new ArrayList<>();
        this.resultClass = Objects.requireNonNull(resultClass, "resultClass must not be null");
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        var resultBuilder = ToolProviderResult.builder();
        Set<String> immediateToolReturnResults = new HashSet<>();
        toolObjects.forEach(toolObject -> Arrays.stream(toolObject.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .forEach(toolMethod -> addToolProvider(toolObject, toolMethod, resultBuilder)));
        Arrays.stream(resultClass.getMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .forEach(toolMethod -> {
                    addToolProvider(resultClass, toolMethod, resultBuilder);
                    immediateToolReturnResults.add(toolMethod.getName());
                });
        resultBuilder.immediateReturnToolNames(immediateToolReturnResults);
        return resultBuilder.build();
    }

    private static void addToolProvider(Object toolObject, Method toolMethod, ToolProviderResult.Builder resultBuilder) {
        ToolSpecification specification = toolSpecificationFrom(toolMethod);
        ToolExecutor executor = DefaultToolExecutor.builder()
                .object(toolObject)
                .originalMethod(toolMethod)
                .methodToInvoke(toolMethod)
                .wrapToolArgumentsExceptions(true)
                .propagateToolExecutionExceptions(true)
                .build();
        resultBuilder.add(specification, executor);
    }
}