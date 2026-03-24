package org.tarik.ta.config.scopes;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import io.avaje.inject.InjectModule;
import org.tarik.ta.model.VisualState;

@Factory
@InjectModule(requires = UiAgentRequestScope.class)
public class UiAgentRequestScopeModule {
    private final VisualState visualState;

    public UiAgentRequestScopeModule(VisualState visualState) {
        this.visualState = visualState;
    }

    @Bean
    @UiAgentRequestScope
    public VisualState visualState() {
        return visualState;
    }
}
