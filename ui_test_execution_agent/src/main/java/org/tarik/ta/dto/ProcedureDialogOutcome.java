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
package org.tarik.ta.dto;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * How the operator left the editor of a persisted procedure: {@code procedureId} is the procedure saved or deleted, which is a
 * parent of the edited one when the operator moved up to it, and {@code result} its saved content.
 */
public record ProcedureDialogOutcome(@Nullable UUID procedureId, @Nullable IngestionNode result, boolean cancelled,
                                     boolean deleted) {

    public static ProcedureDialogOutcome saved(UUID procedureId, IngestionNode result) {
        return new ProcedureDialogOutcome(procedureId, result, false, false);
    }

    public static ProcedureDialogOutcome cancelledOutcome() {
        return new ProcedureDialogOutcome(null, null, true, false);
    }

    public static ProcedureDialogOutcome deletedOutcome(UUID procedureId) {
        return new ProcedureDialogOutcome(procedureId, null, false, true);
    }
}
