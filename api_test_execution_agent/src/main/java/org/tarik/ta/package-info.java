@io.avaje.inject.InjectModule(requires = {
    org.tarik.ta.core.AgentConfig.class,
    org.tarik.ta.core.manager.BudgetManager.class,
    org.tarik.ta.core.model.ModelFactory.class,
    org.tarik.ta.core.utils.TestCaseExtractor.class,
    org.tarik.ta.core.AbstractServer.class,
    org.tarik.ta.core.a2a.AgentExecutionResource.class
})
package org.tarik.ta;