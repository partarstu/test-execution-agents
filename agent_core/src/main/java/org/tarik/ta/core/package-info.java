@io.avaje.inject.InjectModule(
        provides = {
                org.tarik.ta.core.AgentConfig.class,
                org.tarik.ta.core.manager.BudgetManager.class,
                org.tarik.ta.core.config.scopes.BaseAgentRequestScope.class,
                org.tarik.ta.core.model.ModelFactory.class,
                org.tarik.ta.core.model.TestExecutionContext.class,
                org.tarik.ta.core.model.ChatModelEventListener.class,
                org.tarik.ta.core.tools.TestContextDataTools.class,
                org.tarik.ta.core.utils.TestCaseExtractor.class,
                org.tarik.ta.core.utils.LogCapture.class,
                org.tarik.ta.core.AbstractServer.class,
                org.tarik.ta.core.a2a.AgentExecutionResource.class
        },
        requires = {
                org.tarik.ta.core.a2a.AgentExecutor.class,
                io.a2a.spec.AgentCard.class
        }
)
package org.tarik.ta.core;
