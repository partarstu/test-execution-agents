/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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
package org.tarik.ta.dto;

/**
 * The kind of a test case item, which also gives the prefix of its ID (P1, P2, … for preconditions, S1, S2, … for test steps).
 */
public enum ItemKind {
    PRECONDITION("P"),
    STEP("S");

    private final String idPrefix;

    ItemKind(String idPrefix) {
        this.idPrefix = idPrefix;
    }

    public String idPrefix() {
        return idPrefix;
    }
}
