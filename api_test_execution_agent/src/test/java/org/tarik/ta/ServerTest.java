/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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
import io.avaje.inject.BeanScopeBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tarik.ta.core.AbstractServer;
import org.tarik.ta.core.telemetry.GenAiMetrics;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerTest {

    @Test
    void main_shouldExistAsStaticBootstrapEntryPoint() throws NoSuchMethodException {
        var mainMethod = Server.class.getDeclaredMethod("main");

        assertThat(Modifier.isStatic(mainMethod.getModifiers())).isTrue();
        assertThat(mainMethod.getReturnType()).isEqualTo(void.class);
    }

    @Test
    void main_shouldKeepTheBeanScopeOpen_whenTheServerHasStarted() {
        var builder = mock(BeanScopeBuilder.class, RETURNS_SELF);
        var scope = mock(BeanScope.class);
        var server = mock(AbstractServer.class);
        when(builder.build()).thenReturn(scope);
        when(scope.get(AbstractServer.class)).thenReturn(server);

        try (MockedStatic<BeanScope> beanScope = mockStatic(BeanScope.class)) {
            beanScope.when(BeanScope::builder).thenReturn(builder);

            Server.main();
        }

        verify(builder).shutdownHook(true);
        verify(server).start();
        verify(scope, never()).close();
    }

    @Test
    void beanScope_shouldWireTheAgentBeansUnambiguously() {
        try (var scope = BeanScope.builder().build()) {
            assertThat(scope.get(AbstractServer.class)).isNotNull();
            assertThat(scope.get(GenAiMetrics.class)).isNotNull();
        }
    }
}
