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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.tarik.ta.core.dto.FinalResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
     * Creates an InheritanceAwareToolProvider that scans the provided tool objects.
     *
     * @param toolObjects Collection of objects containing @Tool annotated methods (including inherited ones)
     * @param resultClass The class of the final result, which may also contain static @Tool methods. Must not be null.
     */
    public InheritanceAwareToolProvider(Collection<?> toolObjects, Class<T> resultClass) {
        this.toolObjects = new ArrayList<>(toolObjects);
        this.resultClass = Objects.requireNonNull(resultClass, "resultClass must not be null");
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        var resultBuilder = ToolProviderResult.builder();

        for (Object toolObject : toolObjects) {
            if (toolObject == null) {
                continue;
            }
            // Use getMethods() to get all public methods including inherited ones
            for (Method method : toolObject.getClass().getMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    ToolSpecification specification = toolSpecificationFrom(method);
                    ToolExecutor executor = new DefaultToolExecutor(toolObject, method);
                    resultBuilder.add(specification, executor);
                }
            }
        }

        for (Method method : resultClass.getMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                ToolSpecification specification = toolSpecificationFrom(method);
                ToolExecutor executor = new DefaultToolExecutor(null, method);
                resultBuilder.add(specification, executor);
            }
        }

        return resultBuilder.build();
    }
}