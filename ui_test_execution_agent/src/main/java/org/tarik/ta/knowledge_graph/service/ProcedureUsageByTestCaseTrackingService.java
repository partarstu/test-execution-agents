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
