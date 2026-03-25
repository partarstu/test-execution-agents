package org.tarik.ta.config.scopes;

import io.avaje.inject.InjectModule;
import jakarta.inject.Scope;
import org.tarik.ta.core.config.scopes.BaseAgentRequestScope;

@Scope
@InjectModule(requires = BaseAgentRequestScope.class)
public @interface UiAgentRequestScope {
}
