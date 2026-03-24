package org.tarik.ta.core.config.scopes;

import io.avaje.inject.InjectModule;
import org.tarik.ta.core.dto.TestCase;
import jakarta.inject.Scope;

@Scope
@InjectModule(requires = TestCase.class)
public @interface BaseAgentRequestScope {
}
