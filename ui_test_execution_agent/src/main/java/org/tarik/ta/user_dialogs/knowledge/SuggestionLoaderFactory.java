/*
 * ui-test-execution-agent - ${project.description}
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
