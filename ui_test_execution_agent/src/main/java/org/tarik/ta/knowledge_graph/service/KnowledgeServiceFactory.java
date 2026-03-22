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

import org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder;
import org.tarik.ta.knowledge_graph.repository.*;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;
import org.tarik.ta.knowledge_graph.repository.ProcedureUsageByTestCaseTrackingRepository;
import org.tarik.ta.knowledge_graph.KnowledgeServices;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class KnowledgeServiceFactory {

    private KnowledgeServiceFactory() {
    }

    public static KnowledgeService createKnowledgeService() {
        var embeddingService = new EmbeddingService();
        var procedureRepository = new ProcedureRepository();
        var decompositionService = new DecompositionService(procedureRepository);
        var phraseEmbeddingRepository = new PhraseEmbeddingRepository();
        return new KnowledgeService(procedureRepository, embeddingService, decompositionService, phraseEmbeddingRepository);
    }

    public static KnowledgeIngestionService createKnowledgeIngestionService(
            KnowledgeService knowledgeService,
            SatisfiesEdgeRepository satisfiesEdgeRepository,
            FailureContextService failureContextService) {
        return new KnowledgeIngestionService(knowledgeService.procedureRepository(),
                knowledgeService.embeddingService(), knowledgeService.decompositionService(), satisfiesEdgeRepository, failureContextService,
                knowledgeService.phraseEmbeddingRepository());
    }

    public static KnowledgeServices createKnowledgeServices() {
        var knowledgeService = createKnowledgeService();
        var satisfiesEdgeRepository = new SatisfiesEdgeRepository();
        var satisfiesEdgeService = new SatisfiesEdgeService(satisfiesEdgeRepository, knowledgeService.phraseEmbeddingRepository());
        var failureContextRepository = new FailureContextRepository();
        var failureContextService = new FailureContextService(failureContextRepository);
        var ingestionService = createKnowledgeIngestionService(knowledgeService, satisfiesEdgeRepository, failureContextService);
        var usageTrackingService = new ProcedureUsageByTestCaseTrackingService(new ProcedureUsageByTestCaseTrackingRepository());
        LocationHistoryRecorder locationHistoryRecorder = knowledgeService.procedureRepository()::updateElementStability;
        Function<UUID, Optional<ElementLocationHistory>> stabilityLookup = knowledgeService.procedureRepository()::getElementStability;
        return new KnowledgeServices(knowledgeService, ingestionService, satisfiesEdgeService, usageTrackingService,
                failureContextService, locationHistoryRecorder, stabilityLookup);
    }

    public static GraphHealthRepository createGraphHealthRepository() {
        return new GraphHealthRepository();
    }

    public static GraphHealthService createGraphHealthService() {
        return new GraphHealthService(createGraphHealthRepository());
    }

}
