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
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static com.google.common.base.Preconditions.checkArgument;
import static org.tarik.ta.core.utils.CommonUtils.isNotBlank;

public class ChromaRetriever extends UiElementRetriever {
    private static final Logger LOG = LoggerFactory.getLogger(ChromaRetriever.class);
    private static final int CONNECTION_TIMEOUT_SECONDS = 20;

    public ChromaRetriever(String url) {
        super(createEmbeddingStore(url));
    }

    private static EmbeddingStore<TextSegment> createEmbeddingStore(String url) {
        checkArgument(isNotBlank(url));
        try {
            return ChromaEmbeddingStore
                    .builder()
                    .baseUrl(url)
                    .collectionName(COLLECTION_NAME)
                    .logRequests(true)
                    .logResponses(true)
                    .timeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                    .build();
        } catch (RuntimeException e) {
            String errorMessage = String.format("Failed to connect to ChromaDB at URL: %s. Root cause: ", url);
            LOG.error(errorMessage, e);
            throw e;
        }
    }
}
