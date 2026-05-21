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
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository.UiElementMatch;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.user_dialogs.BoundingBoxCaptureNeededPopup;
import org.tarik.ta.user_dialogs.UiElementInfoPopup;
import org.tarik.ta.user_dialogs.UiElementInfoPopup.UiElementInfo;
import org.tarik.ta.user_dialogs.UiElementScreenshotCaptureWindow;
import org.tarik.ta.user_dialogs.UiElementScreenshotCaptureWindow.UiElementCaptureResult;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.Screenshot.fromBufferedImage;

@Singleton
public class UiElementRefinementHelper {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementRefinementHelper.class);
    protected static final int USER_DIALOG_DISMISS_DELAY_MILLIS = 2000;

    private final AgentConfig agentConfig;
    private final UiTestAgentConfig uiTestAgentConfig;

    public UiElementRefinementHelper(AgentConfig agentConfig, UiTestAgentConfig uiTestAgentConfig) {
        this.agentConfig = agentConfig;
        this.uiTestAgentConfig = uiTestAgentConfig;
    }

    public Optional<UiElement> promptUserToUpdateElementScreenshot(UiElementRepository repository, UUID elementId) {
        return findElementById(repository, elementId).flatMap(elementToUpdate -> {
            LOG.info("User chose to update screenshot for element: {}", elementToUpdate.name());

            BoundingBoxCaptureNeededPopup.display(null, uiTestAgentConfig);
            sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);

            return UiElementScreenshotCaptureWindow.displayAndGetResult(null, Color.GREEN, uiTestAgentConfig)
                    .filter(UiElementCaptureResult::success)
                    .map(captureResult -> {
                        var newScreenshot = fromBufferedImage(captureResult.elementScreenshot(), "png");
                        var elementWithNewScreenshot = new UiElement(elementToUpdate.id(), elementToUpdate.name(),
                                elementToUpdate.description(), elementToUpdate.locationDetails(), elementToUpdate.parentElementSummary(),
                                newScreenshot, elementToUpdate.isDataDependent());
                        repository.update(elementWithNewScreenshot, false);
                        LOG.debug("Persisted updated screenshot for element: {}", elementToUpdate.name());
                        return elementWithNewScreenshot;
                    });
        });
    }

    public Optional<UiElement> promptUserToUpdateElementInfo(UiElementRepository repository, UUID elementId) {
        LOG.info("User chose to update info for element with ID {}", elementId);
        return findElementById(repository, elementId)
                .flatMap(elementToUpdate -> {
                    var currentInfo = new UiElementInfo(elementToUpdate.name(), elementToUpdate.description(),
                            elementToUpdate.locationDetails(), elementToUpdate.parentElementSummary(), elementToUpdate.isDataDependent());
                    return UiElementInfoPopup.displayAndGetUpdatedElementInfo(null, currentInfo, uiTestAgentConfig)
                            .map(newInfo -> {
                                var updatedElement = new UiElement(elementToUpdate.id(), newInfo.name(), newInfo.description(),
                                        newInfo.locationDetails(), newInfo.pageSummary(), elementToUpdate.screenshot(),
                                        newInfo.isDataDependent());
                                repository.update(updatedElement);
                                LOG.debug("Persisted updated info for element: {}", updatedElement.name());
                                return updatedElement;
                            });
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