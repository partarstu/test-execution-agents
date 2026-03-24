@InjectModule(requires = {
        AgentConfig.class,
        BudgetManager.class,
        ModelFactory.class,
        TestCaseExtractor.class,
        AbstractServer.class,
        AgentExecutionResource.class
})
package org.tarik.ta;

import io.avaje.inject.InjectModule;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.core.AbstractServer;
import org.tarik.ta.core.a2a.AgentExecutionResource;
