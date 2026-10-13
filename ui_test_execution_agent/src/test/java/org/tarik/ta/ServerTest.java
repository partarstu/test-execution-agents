/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
package org.tarik.ta;

import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.AbstractServer;
import org.tarik.ta.panel.PanelServer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerTest {

    @Test
    void start_shouldStartThePanelServerAndThenTheAgentServer() {
        var scope = mock(BeanScope.class);
        var panelServer = mock(PanelServer.class);
        var agentServer = mock(AbstractServer.class);
        when(scope.get(PanelServer.class)).thenReturn(panelServer);
        when(scope.get(AbstractServer.class)).thenReturn(agentServer);

        Server.start(scope);

        var inOrder = inOrder(panelServer, agentServer);
        inOrder.verify(panelServer).start();
        inOrder.verify(agentServer).start();
    }

    @Test
    void start_shouldNotOpenTheAgentPort_whenThePanelConfigurationIsRefused() {
        var scope = mock(BeanScope.class);
        var panelServer = mock(PanelServer.class);
        var agentServer = mock(AbstractServer.class);
        when(scope.get(PanelServer.class)).thenReturn(panelServer);
        doThrow(new IllegalStateException("panel.password must be set")).when(panelServer).start();

        assertThatThrownBy(() -> Server.start(scope)).isInstanceOf(IllegalStateException.class);

        verify(agentServer, never()).start();
    }
}
