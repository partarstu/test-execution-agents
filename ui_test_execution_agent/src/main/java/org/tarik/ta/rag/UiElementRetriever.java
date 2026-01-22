/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel;
import dev.langchain4j.store.embedding.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.rag.model.UiElement;

import java.util.Comparator;
import java.util.List;

public abstract class UiElementRetriever {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected static final String COLLECTION_NAME = "ui_elements";
    protected final EmbeddingStore<TextSegment> embeddingStore;
    protected static final EmbeddingModel embeddingModel = new BgeSmallEnV15EmbeddingModel();

    protected UiElementRetriever(EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    public void storeElement(UiElement uiElement) {
        var segment = uiElement.asTextSegment();
        var embedding = embeddingModel.embed(segment).content();
        embeddingStore.addAll(List.of(uiElement.uuid().toString()), List.of(embedding), List.of(segment));
        log.info("Inserted UiElement '{}' into the vector DB", uiElement.name());
    }

    public List<RetrievedUiElementItem> retrieveUiElements(String nameQuery, int topN, double minScore) {
        var queryEmbedding = embeddingModel.embed(nameQuery).content();
        var searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .minScore(minScore)
                .maxResults(topN)
                .build();
        var result = embeddingStore.search(searchRequest);
        var resultingItems = result.matches().stream()
                .sorted(Comparator.<EmbeddingMatch<TextSegment>>comparingDouble(EmbeddingMatch::score).reversed())
                .map(match -> {
                    var element = UiElement.fromTextSegment(match.embedded());
                    return new RetrievedUiElementItem(element, match.score());
                })
                .peek(item -> log.info("Retrieved UI element from DB: name='{}', mainScore={}",
                        item.element().name(), item.mainScore()))
                .distinct()
                .toList();
        log.info("Retrieved {} most matching results to the query '{}'", resultingItems.size(), nameQuery);
        return resultingItems;
    }



    public void updateElement(UiElement originalUiElement, UiElement updatedUiElement) {
        removeElement(originalUiElement);
        storeElement(updatedUiElement);
    }

    public void removeElement(UiElement uiElement) {
        embeddingStore.remove(uiElement.uuid().toString());
        log.info("Removed UiElement '{}' from the vector DB", uiElement.name());
    }

    public record RetrievedUiElementItem(UiElement element, double mainScore) {
    }
}
