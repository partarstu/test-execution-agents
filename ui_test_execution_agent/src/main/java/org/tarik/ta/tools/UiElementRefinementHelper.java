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

    public Optional<UiElement> updateElementScreenshot(UiElementRepository repository, UUID elementId) {
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
                        repository.update(elementWithNewScreenshot);
                        LOG.debug("Persisted updated screenshot for element: {}", elementToUpdate.name());
                        return elementWithNewScreenshot;
                    });
        });
    }

    public Optional<UiElement> updateElementInfo(UiElementRepository repository, UUID elementId) {
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

    public void deleteElement(UiElementRepository repository, UUID elementId) {
        Optional<UiElement> elementOpt = findElementById(repository, elementId);
        LOG.info("User chose to delete element with ID {}", elementId);
        if (elementOpt.isPresent()) {
            UiElement elementToDelete = elementOpt.get();
            repository.remove(elementToDelete);
        } else {
            LOG.warn("Element with ID {} not found", elementId);
        }
    }

    public Optional<UiElement> findElementById(UiElementRepository repository, UUID elementId) {
        return repository.findById(elementId);
    }

    public List<UiElementMatch> retrieveUiElements(UiElementRepository repository, String query) {
        return repository.findBySemanticSearch(query, agentConfig.getRetrieverTopN(),
                uiTestAgentConfig.getElementRetrievalMinGeneralScore());
    }
}
