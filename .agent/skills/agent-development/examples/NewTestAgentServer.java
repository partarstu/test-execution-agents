package org.tarik.ta;

import io.a2a.spec.AgentCard;
import org.tarik.ta.a2a.AgentCardProducer;
import org.tarik.ta.a2a.NewAgentExecutor;
import org.tarik.ta.core.AbstractServer;
import org.tarik.ta.core.a2a.AgentExecutor;

public class NewTestAgentServer extends AbstractServer {

    @Override
    protected AgentExecutor createAgentExecutor() {
        return new NewAgentExecutor();
    }

    @Override
    protected AgentCard createAgentCard() {
        return AgentCardProducer.createAgentCard();
    }

    @Override
    protected String getStartupLogMessage(String host, int port) {
        return "New Test Agent Server started on %s:%d".formatted(host, port);
    }

    public static void main(String[] args) {
        new NewTestAgentServer().start();
    }
}
