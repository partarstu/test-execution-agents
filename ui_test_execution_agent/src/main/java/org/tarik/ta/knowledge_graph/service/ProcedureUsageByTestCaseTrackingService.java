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
package org.tarik.ta.knowledge_graph.service;

import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.repository.ProcedureUsageByTestCaseTrackingRepository;

import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Service for tracking which test cases use which procedures via {@code USES_PROCEDURE} edges.
 * Exposes usage queries and lifecycle management without leaking the raw repository.
 */
@Singleton
public class ProcedureUsageByTestCaseTrackingService {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureUsageByTestCaseTrackingService.class);

    private final ProcedureUsageByTestCaseTrackingRepository procedureUsageByTestCaseTrackingRepository;

    public ProcedureUsageByTestCaseTrackingService(ProcedureUsageByTestCaseTrackingRepository procedureUsageByTestCaseTrackingRepository) {
        this.procedureUsageByTestCaseTrackingRepository = requireNonNull(procedureUsageByTestCaseTrackingRepository, "usageTrackingRepository");
    }

    public void mergeUsesProcedure(String testCaseName, UUID procedureId) {
        LOG.debug("Merging USES_PROCEDURE edge: test case '{}' → procedure {}", testCaseName, procedureId);
        procedureUsageByTestCaseTrackingRepository.mergeUsesProcedure(testCaseName, procedureId);
    }

    public void cleanupStaleUsesProcedure(String testCaseName, List<UUID> usedProcedureIds) {
        LOG.debug("Cleaning up stale USES_PROCEDURE edges for test case '{}' (keeping {} procedure(s))", testCaseName, usedProcedureIds.size());
        procedureUsageByTestCaseTrackingRepository.cleanupStaleUsesProcedure(testCaseName, usedProcedureIds);
    }

    public List<String> findTestCasesUsingProcedure(UUID procedureId) {
        var result = procedureUsageByTestCaseTrackingRepository.findTestCasesUsingProcedure(procedureId);
        LOG.debug("Procedure {} is used by {} test case(s): {}", procedureId, result.size(), result);
        return result;
    }
}
