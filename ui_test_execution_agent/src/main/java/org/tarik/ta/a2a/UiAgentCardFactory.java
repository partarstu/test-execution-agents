package org.tarik.ta.a2a;

import io.a2a.spec.AgentCard;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Singleton;
import org.tarik.ta.core.AgentConfig;

@Factory
public class UiAgentCardFactory {

    private final AgentConfig agentConfig;

    public UiAgentCardFactory(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }

    @Bean
    @Singleton
    public AgentCard agentCard() {
        return new AgentCardProducer(agentConfig.getExternalUrl()).agentCard();
    }
}
