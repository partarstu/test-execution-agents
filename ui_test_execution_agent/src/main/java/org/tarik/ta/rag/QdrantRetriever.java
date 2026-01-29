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
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkArgument;
import static org.tarik.ta.core.utils.CommonUtils.isNotBlank;

public class QdrantRetriever extends UiElementRetriever {
    private static final Logger LOG = LoggerFactory.getLogger(QdrantRetriever.class);
    public static final int HTTPS_PORT = 443;
    public static final int QDRANT_GRPC_PORT = 6334;

    public QdrantRetriever(String url, String apiKey) {
        super(createEmbeddingStore(url, apiKey));
    }

    private static EmbeddingStore<TextSegment> createEmbeddingStore(String urlStr, String apiKey) {
        checkArgument(isNotBlank(urlStr));
        try {
            var fullUrl = urlStr.contains("://") ? urlStr : "http://" + urlStr;
            var uri = URI.create(fullUrl);
            var host = uri.getHost();
            var port = uri.getPort();
            var useTls = "https".equalsIgnoreCase(uri.getScheme());
            var finalPort = port >= 0 ? port : (useTls ? HTTPS_PORT : QDRANT_GRPC_PORT);
            var clientBuilder = QdrantGrpcClient.newBuilder(host, finalPort, useTls);
            if (isNotBlank(apiKey)) {
                clientBuilder.withApiKey(apiKey);
            }

            LOG.info("Connecting to Qdrant at {}:{}, using TLS={}", host, finalPort, useTls);
            var client = new QdrantClient(clientBuilder.build());
            ensureCollectionExists(client);

            return QdrantEmbeddingStore.builder()
                    .collectionName(COLLECTION_NAME)
                    .client(client)
                    .build();
        } catch (RuntimeException e) {
            String errorMessage = String.format("Failed to connect to QdrantDB at URL: %s. Root cause: ", urlStr);
            LOG.error(errorMessage, e);
            throw e;
        }
    }

    private static void ensureCollectionExists(QdrantClient client) {
        try {
            var exists = client.listCollectionsAsync().get().stream()
                    .anyMatch(collection -> collection.equals(COLLECTION_NAME));
            if (!exists) {
                var dimension = embeddingModel.embed("dummy").content().dimension();
                client.createCollectionAsync(COLLECTION_NAME,
                                VectorParams.newBuilder().setSize(dimension).setDistance(Distance.Cosine).build())
                        .get();
                LOG.info("Created collection '{}' in Qdrant", COLLECTION_NAME);
            }
        } catch (Exception e) {
            LOG.warn("Failed to ensure Qdrant collection '{}' exists", COLLECTION_NAME, e);
        }
    }
}
