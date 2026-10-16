/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.a2a;

import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.core.telemetry.TokenUsageRecord;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TaskTokenUsageTest {
    private static final TokenUsageRecord USAGE = new TokenUsageRecord("item_selection", "google", "model", "model", "host", 7, 3, 0,
            100);

    @Mock
    private AgentEmitter emitter;

    @Test
    void emit_shouldAddTheUsageAsTokenUsageArtifact() {
        TaskTokenUsage.emit(emitter, "task-1", List.of(USAGE));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Part<?>>> partsCaptor = ArgumentCaptor.forClass(List.class);
        verify(emitter).addArtifact(partsCaptor.capture(), isNull(), eq("token_usage"), isNull());
        assertThat(partsCaptor.getValue()).singleElement().isInstanceOfSatisfying(TextPart.class, part ->
                assertThat(part.text()).contains("\"operationName\":\"item_selection\"", "\"inputTokens\":7", "\"outputTokens\":3"));
    }

    @Test
    void emit_shouldEmitNothing_whenTheTaskUsedNoTokens() {
        TaskTokenUsage.emit(emitter, "task-1", List.of());

        verifyNoInteractions(emitter);
    }

    @Test
    void emit_shouldNotThrow_whenTheArtifactCannotBeEmitted() {
        doThrow(new IllegalStateException("queue closed")).when(emitter).addArtifact(anyList(), any(), any(), any());

        assertThatCode(() -> TaskTokenUsage.emit(emitter, "task-1", List.of(USAGE))).doesNotThrowAnyException();
    }
}
