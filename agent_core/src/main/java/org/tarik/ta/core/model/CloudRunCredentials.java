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
package org.tarik.ta.core.model;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;

/**
 * Resolves the credential an OpenAI-compatible endpoint expects, keeping the configured key for any endpoint not served by Cloud
 * Run. Cloud Run authenticates through IAM with identity tokens that live at most an hour, so the token is minted from
 * application default credentials instead.
 */
public final class CloudRunCredentials {
    private static final String CLOUD_RUN_HOST_SUFFIX = ".run.app";
    private static final String MISSING_ID_TOKEN_PROVIDER_MESSAGE =
            "No application default credentials able to issue an identity token for %s. Run 'gcloud auth application-default " +
                    "login --impersonate-service-account=<invoker service account>' or point GOOGLE_APPLICATION_CREDENTIALS at " +
                    "its key file.";

    private CloudRunCredentials() {
    }

    /**
     * Returns an identity token scoped to the Cloud Run service behind {@code endpoint}, or {@code configuredApiKey} for any
     * other endpoint.
     */
    public static @NotNull String resolve(@NotNull String endpoint, @NotNull String configuredApiKey) {
        var endpointUri = URI.create(endpoint);
        var host = endpointUri.getHost();
        if (host == null || !host.endsWith(CLOUD_RUN_HOST_SUFFIX)) {
            return configuredApiKey;
        }
        return mintIdentityToken("%s://%s".formatted(endpointUri.getScheme(), host));
    }

    private static @NotNull String mintIdentityToken(@NotNull String audience) {
        try {
            if (!(GoogleCredentials.getApplicationDefault() instanceof IdTokenProvider idTokenProvider)) {
                throw new IllegalStateException(MISSING_ID_TOKEN_PROVIDER_MESSAGE.formatted(audience));
            }
            var idTokenCredentials = IdTokenCredentials.newBuilder()
                    .setIdTokenProvider(idTokenProvider)
                    .setTargetAudience(audience)
                    .build();
            idTokenCredentials.refreshIfExpired();
            return idTokenCredentials.getIdToken().getTokenValue();
        } catch (IOException e) {
            var message = MISSING_ID_TOKEN_PROVIDER_MESSAGE.formatted(audience);
            throw new IllegalStateException(message, e);
        }
    }
}
