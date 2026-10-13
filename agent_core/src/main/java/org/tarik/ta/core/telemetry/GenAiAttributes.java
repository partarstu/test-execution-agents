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
package org.tarik.ta.core.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import org.tarik.ta.core.AgentConfig.ModelProvider;

/**
 * The OpenTelemetry semantic-convention attribute keys of the GenAI metrics this agent records. They are defined here
 * rather than taken from the incubating semantic-conventions artifact, whose coordinates and constants still change
 * between releases.
 */
public final class GenAiAttributes {

    /** The sub-agent operation the call belongs to, e.g. {@code test_step_action}. */
    public static final AttributeKey<String> OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    /** The provider serving the model, e.g. {@code gcp.gen_ai} or {@code anthropic}. */
    public static final AttributeKey<String> PROVIDER_NAME = AttributeKey.stringKey("gen_ai.provider.name");
    /** The model the request asked for. */
    public static final AttributeKey<String> REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    /** The model the response reports, which may differ from the requested one. */
    public static final AttributeKey<String> RESPONSE_MODEL = AttributeKey.stringKey("gen_ai.response.model");
    /** {@code input}, {@code output} or {@code cache_read}. */
    public static final AttributeKey<String> TOKEN_TYPE = AttributeKey.stringKey("gen_ai.token.type");
    /** Host of the endpoint the request went to. */
    public static final AttributeKey<String> SERVER_ADDRESS = AttributeKey.stringKey("server.address");

    public static final String TOKEN_TYPE_INPUT = "input";
    public static final String TOKEN_TYPE_OUTPUT = "output";
    public static final String TOKEN_TYPE_CACHE_READ = "cache_read";

    private GenAiAttributes() {
    }

    /**
     * The semantic-convention name of a provider: {@code gcp.gen_ai} or {@code gcp.vertex_ai} for Google depending on the
     * configured API, and their own name for the self-hosted providers, which have no registered one.
     */
    public static String providerName(ModelProvider provider, boolean servedByVertexAi) {
        return switch (provider) {
            case GOOGLE -> servedByVertexAi ? "gcp.vertex_ai" : "gcp.gen_ai";
            case OPENAI -> "openai";
            case ANTHROPIC -> servedByVertexAi ? "gcp.vertex_ai" : "anthropic";
            case GROQ -> "groq";
            case HCOMPANY -> "hcompany";
            case QWEN -> "qwen";
        };
    }
}
