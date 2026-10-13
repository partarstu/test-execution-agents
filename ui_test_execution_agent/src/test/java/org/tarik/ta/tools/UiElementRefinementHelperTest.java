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
package org.tarik.ta.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.dto.UiElementInfo;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UiElementRefinementHelperTest {
    private static final UiElement ELEMENT = new UiElement(UUID.randomUUID(), "Save", "button", "footer", "editor", null, false);

    @Mock
    private AgentConfig agentConfig;
    @Mock
    private UiTestAgentConfig uiTestAgentConfig;
    @Mock
    private UiElementRepository repository;

    private UiElementRefinementHelper helper;

    @BeforeEach
    void setUp() {
        helper = new UiElementRefinementHelper(agentConfig, uiTestAgentConfig);
    }

    @Test
    void updateElementInfo_shouldPersistTheNewInfoAndKeepTheScreenshot_whenTheElementExists() {
        when(repository.findById(ELEMENT.id())).thenReturn(Optional.of(ELEMENT));

        var updated = helper.updateElementInfo(repository, ELEMENT.id(),
                new UiElementInfo("Save", "primary button", "footer", "editor", true));

        assertThat(updated).hasValueSatisfying(element -> {
            assertThat(element.id()).isEqualTo(ELEMENT.id());
            assertThat(element.description()).isEqualTo("primary button");
            assertThat(element.isDataDependent()).isTrue();
        });
        verify(repository).update(updated.orElseThrow());
    }

    @Test
    void updateElementInfo_shouldPersistNothing_whenTheElementIsUnknown() {
        when(repository.findById(ELEMENT.id())).thenReturn(Optional.empty());

        assertThat(helper.updateElementInfo(repository, ELEMENT.id(), new UiElementInfo("a", "b", "c", "d", false))).isEmpty();
        verify(repository, never()).update(any());
    }

    @Test
    void replaceElementScreenshot_shouldPersistTheNewScreenshotWithoutRefreshingTheEmbedding_whenTheElementExists() {
        when(repository.findById(ELEMENT.id())).thenReturn(Optional.of(ELEMENT));
        var persisted = ArgumentCaptor.forClass(UiElement.class);

        var updated = helper.replaceElementScreenshot(repository, ELEMENT.id(), new BufferedImage(3, 4, BufferedImage.TYPE_INT_RGB));

        assertThat(updated).hasValueSatisfying(element -> assertThat(element.screenshot().toBufferedImage().getHeight()).isEqualTo(4));
        verify(repository).update(persisted.capture(), eq(false));
        assertThat(persisted.getValue().id()).isEqualTo(ELEMENT.id());
        assertThat(persisted.getValue().name()).isEqualTo(ELEMENT.name());
    }

    @Test
    void replaceElementScreenshot_shouldPersistNothing_whenTheElementIsUnknown() {
        when(repository.findById(ELEMENT.id())).thenReturn(Optional.empty());

        assertThat(helper.replaceElementScreenshot(repository, ELEMENT.id(), new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)))
                .isEmpty();
        verify(repository, never()).update(any(), anyBoolean());
    }
}
