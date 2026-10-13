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
package org.tarik.ta.knowledge_graph;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.UUID;

/**
 * Outcome of a supervised user decision around an atomic step. Every rerun outcome refetches the procedure
 * from the DB before rerunning it — in-memory {@code Procedure} instances are never reused for a rerun.
 */
sealed interface UserDecisionOutcome {
    record ContinueNextStep() implements UserDecisionOutcome {
    }

    record TerminateExecution() implements UserDecisionOutcome {
    }

    /**
     * The atomic procedure must be re-executed. Produced only inside the step retry loop, which refetches
     * the procedure first and carries the fresh instance here.
     */
    record RerunAtomicProcedure(Procedure procedure) implements UserDecisionOutcome {
    }

    /**
     * The whole test step must be rerun from its first atomic step, abandoning any composite scopes. A non-null {@code
     * replacementProcedureId} names a procedure created in the same dialog session to rerun instead of the current root.
     */
    record RerunTestStep(@Nullable UUID replacementProcedureId) implements UserDecisionOutcome {
    }
}
