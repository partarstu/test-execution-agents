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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tarik.ta.core.telemetry.GenAiAttributes.SERVER_ADDRESS;

class TokenUsageRecordTest {

    private static final TokenUsageRecord RECORD =
            new TokenUsageRecord("test_step_action", "openai", "gpt-test", "gpt-test-0815", "api.example.org", 10, 20, 5, 1500);

    @Test
    void plus_shouldAddTheCountsAndKeepTheAttributesOfTheReceiver() {
        var other = new TokenUsageRecord("test_step_action", "openai", "gpt-test", "gpt-test-0816", null, 1, 2, 3, 500);

        var sum = RECORD.plus(other);

        assertThat(sum.inputTokens()).isEqualTo(11);
        assertThat(sum.outputTokens()).isEqualTo(22);
        assertThat(sum.cachedTokens()).isEqualTo(8);
        assertThat(sum.durationMillis()).isEqualTo(2000);
        assertThat(sum.totalTokens()).isEqualTo(41);
        assertThat(sum.responseModel()).isEqualTo("gpt-test-0815");
        assertThat(sum.serverAddress()).isEqualTo("api.example.org");
    }

    @Test
    void toAttributes_shouldOmitTheServerAddress_whenThereIsNone() {
        var withoutAddress = new TokenUsageRecord("test_step_action", "gcp.gen_ai", "gemini", "gemini", null, 1, 2, 0, 10);

        assertThat(RECORD.toAttributes().get(SERVER_ADDRESS)).isEqualTo("api.example.org");
        assertThat(withoutAddress.toAttributes().get(SERVER_ADDRESS)).isNull();
    }

    @Test
    void toLogLine_shouldStateTheOperationAndTheCounts() {
        assertThat(RECORD.toLogLine())
                .isEqualTo("operation = test_step_action, provider = openai, model = gpt-test-0815, input tokens = 10, " +
                        "output tokens = 20, cached tokens = 5, total tokens = 35, duration = 1500 ms");
    }

    @Test
    void shouldSerializeIntoTheTokenUsageArtifactWithTheSameNames() throws Exception {
        var json = new ObjectMapper().writeValueAsString(RECORD);

        assertThat(json).isEqualTo("{\"operationName\":\"test_step_action\",\"providerName\":\"openai\"," +
                "\"requestModel\":\"gpt-test\",\"responseModel\":\"gpt-test-0815\",\"serverAddress\":\"api.example.org\"," +
                "\"inputTokens\":10,\"outputTokens\":20,\"cachedTokens\":5,\"durationMillis\":1500}");
    }
}
