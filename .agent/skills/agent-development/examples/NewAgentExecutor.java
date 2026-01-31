package org.tarik.ta.a2a;

import io.a2a.spec.Part;
import org.tarik.ta.NewTestAgent;
import org.tarik.ta.core.a2a.AbstractAgentExecutor;
import org.tarik.ta.core.dto.TestExecutionResult;

import java.util.List;
import java.util.Optional;

public class NewAgentExecutor extends AbstractAgentExecutor {

    @Override
    protected TestExecutionResult executeTestCase(String message) {
        return NewTestAgent.executeTestCase(message);
    }

    @Override
    protected void addSpecificArtifacts(TestExecutionResult result, List<Part<?>> parts) {
        // Add agent-specific artifacts (screenshots, logs, etc.)
    }

    @Override
    protected Optional<List<String>> extractLogs(TestExecutionResult result) {
        return Optional.ofNullable(result.getLogs());
    }
}
