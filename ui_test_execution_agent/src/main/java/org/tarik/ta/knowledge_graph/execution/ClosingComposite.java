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
package org.tarik.ta.knowledge_graph.execution;

import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A composite procedure whose scope closes when the current atomic procedure succeeds.
 *
 * @param effects          the composite's own effects, resolved on first use
 * @param negatedEffectIds returns the ids of the given visible effects that the composite negates
 */
public record ClosingComposite(Supplier<List<PhraseEmbedding>> effects, Function<Set<UUID>, Set<UUID>> negatedEffectIds) {
}
