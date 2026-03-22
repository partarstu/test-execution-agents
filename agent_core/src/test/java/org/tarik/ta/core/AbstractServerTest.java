/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.core;

import io.a2a.spec.AgentCard;
import io.javalin.Javalin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tarik.ta.core.a2a.AgentExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AbstractServerTest {

    @Test
    void start_shouldInitializeAndStartServer() {
        AgentExecutor executor = mock(AgentExecutor.class);
        io.a2a.spec.AgentCapabilities capabilities = new io.a2a.spec.AgentCapabilities(false, false, false, java.util.List.of());
        AgentCard card = new AgentCard("test", "desc", "url", null, "1.0", "doc", capabilities, java.util.List.of(), java.util.List.of(), java.util.List.of(), false, java.util.Map.of(), java.util.List.of(), "icon", java.util.List.of(), "JSONRPC", "1.0", java.util.List.of());

        AbstractServer server = new AbstractServer() {
            @Override
            protected AgentExecutor createAgentExecutor() {
                return executor;
            }

            @Override
            protected AgentCard createAgentCard() {
                return card;
            }

            @Override
            protected String getStartupLogMessage(String host, int port) {
                return "Started on " + host + ":" + port;
            }
        };

        Javalin mockJavalin = mock(Javalin.class);
        when(mockJavalin.start(anyString(), anyInt())).thenReturn(mockJavalin);

        try (MockedStatic<Javalin> mockedJavalin = mockStatic(Javalin.class)) {
            mockedJavalin.when(() -> Javalin.create(any())).thenReturn(mockJavalin);

            server.start();

            mockedJavalin.verify(() -> Javalin.create(any()));
            verify(mockJavalin).start(anyString(), anyInt());
        }
    }
}
