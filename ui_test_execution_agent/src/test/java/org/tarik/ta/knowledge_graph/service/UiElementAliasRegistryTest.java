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
package org.tarik.ta.knowledge_graph.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UiElementAliasRegistryTest {

    private final UiElementAliasRegistry registry = new UiElementAliasRegistry();

    @Test
    void shouldKeepTheSameAliasForTheSameElement() {
        var elementId = UUID.randomUUID();

        var alias = registry.aliasFor(elementId);

        assertThat(alias).isEqualTo(registry.aliasFor(elementId));
    }

    @Test
    void shouldGiveDistinctAliasesToDistinctElements() {
        var firstAlias = registry.aliasFor(UUID.randomUUID());
        var secondAlias = registry.aliasFor(UUID.randomUUID());

        assertThat(firstAlias).isNotEqualTo(secondAlias);
    }

    @Test
    void shouldResolveAnAliasBackToItsElement() {
        var elementId = UUID.randomUUID();

        var alias = registry.aliasFor(elementId);

        assertThat(registry.resolve(alias)).contains(elementId);
    }

    @Test
    void shouldResolveAnAliasIgnoringSurroundingWhitespace() {
        var elementId = UUID.randomUUID();

        var alias = registry.aliasFor(elementId);

        assertThat(registry.resolve("  %s ".formatted(alias))).contains(elementId);
    }

    /** IDs also reach the model inside knowledge tool results, and those are not aliased. */
    @Test
    void shouldResolveAPlainUuidThatWasNeverAliased() {
        var elementId = UUID.randomUUID();

        assertThat(registry.resolve(elementId.toString())).contains(elementId);
    }

    @Test
    void shouldNotResolveSomethingWhichIsNeitherAnAliasNorAUuid() {
        assertThat(registry.resolve("no-such-element")).isEmpty();
    }

    @Test
    void shouldTreatABlankIdAsAbsent() {
        assertThat(registry.aliasFor((String) null)).isNull();
        assertThat(registry.aliasFor("   ")).isNull();
    }

    @Test
    void shouldAliasAnIdHeldAsString() {
        var elementId = UUID.randomUUID();

        var alias = registry.aliasFor(elementId.toString());

        assertThat(alias).isEqualTo(registry.aliasFor(elementId));
    }
}
