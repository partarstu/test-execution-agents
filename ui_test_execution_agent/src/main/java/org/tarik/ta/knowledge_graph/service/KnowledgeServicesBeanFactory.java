package org.tarik.ta.knowledge_graph.service;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Singleton;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

@Factory
public class KnowledgeServicesBeanFactory {
    private final ProcedureRepository procedureRepository;

    public KnowledgeServicesBeanFactory(ProcedureRepository procedureRepository) {
        this.procedureRepository = procedureRepository;
    }

    @Bean
    @Singleton
    public LocationHistoryRecorder locationHistoryRecorder() {
        return procedureRepository::updateElementStability;
    }

    @Bean
    @Singleton
    public ElementLocationHistoryLookup elementStabilityLookup() {
        return procedureRepository::getElementLocationHistory;
    }
}
