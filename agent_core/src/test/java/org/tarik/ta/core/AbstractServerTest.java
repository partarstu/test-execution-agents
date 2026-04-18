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

import io.javalin.Javalin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tarik.ta.core.a2a.AgentExecutionResource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AbstractServerTest {

    @Test
    void start_shouldInitializeAndStartServer() {
        var resource = mock(AgentExecutionResource.class);
        var config = mock(AgentConfig.class);
        when(config.getStartPort()).thenReturn(8080);
        when(config.getHost()).thenReturn("localhost");

        AbstractServer server = new AbstractServer(resource, config);

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
