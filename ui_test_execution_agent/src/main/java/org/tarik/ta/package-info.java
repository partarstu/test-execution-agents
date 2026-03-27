@InjectModule(requires = {
        AgentConfig.class,
        BaseAgentRequestScope.class,
        BudgetManager.class,
        ModelFactory.class,
        TestExecutionContext.class,
        TestContextDataTools.class,
        LogCapture.class,
        TestCaseExtractor.class,
        AbstractServer.class,
        AgentExecutionResource.class,
        VisualState.class
})
package org.tarik.ta;

import io.avaje.inject.InjectModule;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.core.tools.TestContextDataTools;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.core.AbstractServer;
import org.tarik.ta.core.a2a.AgentExecutionResource;
import org.tarik.ta.core.config.scopes.BaseAgentRequestScope;
import org.tarik.ta.model.VisualState;