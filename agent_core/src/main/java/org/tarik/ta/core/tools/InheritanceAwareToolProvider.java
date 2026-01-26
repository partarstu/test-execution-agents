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
        ToolExecutor executor = new DefaultToolExecutor(toolObject, toolMethod);
        resultBuilder.add(specification, executor);
    }
}