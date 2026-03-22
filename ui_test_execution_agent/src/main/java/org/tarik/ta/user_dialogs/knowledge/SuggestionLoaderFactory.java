/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.user_dialogs.knowledge;

import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.List;
import java.util.function.Supplier;

/**
 * Creates {@link SuggestionLoader} instances with the projected execution graph baked in.
 * The {@code precedingAtomicsSupplier} provides the atomic procedures that will execute
 * before the procedure being defined (evaluated lazily at suggestion-load time).
 */
@FunctionalInterface
public interface SuggestionLoaderFactory {
    SuggestionLoader create(Supplier<List<Procedure>> precedingAtomicsSupplier);
}
