package org.tarik.ta.core.config.scopes;

import io.avaje.inject.InjectModule;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.tarik.ta.core.dto.TestCase;

@Factory
@InjectModule(requires = BaseAgentRequestScope.class)
public class BaseAgentRequestScopeModule {
    private final TestCase testCase;

    public BaseAgentRequestScopeModule(TestCase testCase) {
        this.testCase = testCase;
    }

    @Bean
    @BaseAgentRequestScope
    public TestCase testCase() {
        return testCase;
    }
}
