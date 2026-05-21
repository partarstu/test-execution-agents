/*
 * agent-core - ${project.description}
 * Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
