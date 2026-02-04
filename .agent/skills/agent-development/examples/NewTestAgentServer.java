/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
