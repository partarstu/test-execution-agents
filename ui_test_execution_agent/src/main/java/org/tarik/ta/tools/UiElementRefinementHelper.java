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

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.dto.UiElementInfo;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository.UiElementMatch;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.tarik.ta.knowledge_graph.model.node.UiElement.Screenshot.fromBufferedImage;

@Singleton
public class UiElementRefinementHelper {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementRefinementHelper.class);

    private final AgentConfig agentConfig;
    private final UiTestAgentConfig uiTestAgentConfig;

    public UiElementRefinementHelper(AgentConfig agentConfig, UiTestAgentConfig uiTestAgentConfig) {
        this.agentConfig = agentConfig;
        this.uiTestAgentConfig = uiTestAgentConfig;
    }

    /** Replaces the element's screenshot, returning the updated element, or empty when no element has the id. */
    public Optional<UiElement> replaceElementScreenshot(UiElementRepository repository, UUID elementId, BufferedImage screenshot) {
        return findElementById(repository, elementId).map(elementToUpdate -> {
            var elementWithNewScreenshot = new UiElement(elementToUpdate.id(), elementToUpdate.name(), elementToUpdate.description(),
                    elementToUpdate.locationDetails(), elementToUpdate.parentElementSummary(), fromBufferedImage(screenshot, "png"),
                    elementToUpdate.isDataDependent());
            repository.update(elementWithNewScreenshot, false);
            LOG.info("Persisted the operator's new screenshot of element '{}'", elementToUpdate.name());
            return elementWithNewScreenshot;
        });
    }

    /** Replaces the element's info, returning the updated element, or empty when no element has the id. */
    public Optional<UiElement> updateElementInfo(UiElementRepository repository, UUID elementId, UiElementInfo newInfo) {
        return findElementById(repository, elementId).map(elementToUpdate -> {
            var updatedElement = new UiElement(elementToUpdate.id(), newInfo.name(), newInfo.description(), newInfo.locationDetails(),
                    newInfo.pageSummary(), elementToUpdate.screenshot(), newInfo.isDataDependent());
            repository.update(updatedElement);
            LOG.info("Persisted the operator's info of element '{}'", updatedElement.name());
            return updatedElement;
        });
    }

    public List<UiElementMatch> retrieveUiElementsWithMinimumSimilarity(UiElementRepository repository, String query) {
        return repository.findBySemanticSearch(query, agentConfig.getRetrieverTopN(),
                uiTestAgentConfig.getElementRetrievalMinGeneralScore());
    }

    public List<UiElementMatch> retrieveUiElementsWithTargetSimilarity(UiElementRepository repository, String query) {
        return repository.findBySemanticSearch(query, agentConfig.getRetrieverTopN(),
                uiTestAgentConfig.getElementRetrievalMinTargetScore());
    }

    private Optional<UiElement> findElementById(UiElementRepository repository, UUID elementId) {
        return repository.findById(elementId);
    }
}