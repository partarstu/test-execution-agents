/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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
package org.tarik.ta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;

import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual integration tests for the Petstore API.
 * These tests cover all pet-related endpoints from <a href="https://petstore.swagger.io/">...</a>
 *
 * <p>
 * Covered endpoints:
 * <ul>
 * <li>POST /pet - Add a new pet to the store</li>
 * <li>PUT /pet - Update an existing pet</li>
 * <li>GET /pet/findByStatus - Find pets by status</li>
 * <li>GET /pet/findByTags - Find pets by tags (deprecated)</li>
 * <li>GET /pet/{petId} - Find pet by ID</li>
 * <li>POST /pet/{petId} - Update a pet with form data</li>
 * <li>DELETE /pet/{petId} - Delete a pet</li>
 * <li>POST /pet/{petId}/uploadImage - Upload an image for a pet</li>
 * </ul>
 */
@Disabled
@DisplayName("Petstore API - Pet Endpoints Manual Tests")
class ApiManualTest {

    private static final String OPENAPI_SPEC_URL = "https://petstore.swagger.io/v2/swagger.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Full CRUD lifecycle: Create, Read, Update, Delete pet")
    void testPetstoreFlow() throws JsonProcessingException {
        // Given
        var steps = new LinkedList<TestStep>();

        // Load Data
        String testDataPath = Paths.get("src", "test", "resources", "test-data.json").toAbsolutePath().toString();
        var precondition = "Pet data is loaded from '" + testDataPath + "' file into variable as 'target pet'";

        // Create Pet (POST /pet)
        steps.add(new TestStep("Create a new pet by sending a POST request to '/pet'. Use the first item from the " +
                "loaded pet list items as the body. Set 'Content-Type' header to 'application/json'.",
                List.of(),
                "Request is sent. Status: 200."));

        // Extract ID
        steps.add(new TestStep("Store the ID of the created pet into the test context under the variable 'petId'.",
                List.of(),
                "Variable successfully stored in the test context."));

        // Upload Image (POST /pet/{petId}/uploadImage)
        String imagePath = Paths.get("src", "test", "resources", "pet-image.png").toAbsolutePath().toString();
        steps.add(new TestStep("Upload the file '" + imagePath + "' to '/pet/${petId}/uploadImage'. Use multipart name 'file'.",
                List.of("${petId}"),
                "File uploaded. Status: 200"));

        // Get Pet (GET /pet/{petId})
        steps.add(new TestStep("Send a GET request to '/pet/${petId}' to retrieve the pet details.",
                List.of("${petId}"),
                "Request sent. Status: 200. Response has the same JSON field values which were sent in the last request including the " +
                        "uploaded image."));

        // Validate OpenAPI
        steps.add(new TestStep("Validate the last response against the OpenAPI spec at '" + OPENAPI_SPEC_URL + "'.",
                List.of(),
                "OpenAPI validation passed."));

        // Update Pet (PUT /pet)
        steps.add(new TestStep("Update the pet status to 'sold' by sending a PUT request to '/pet'. " +
                "Body: { \"id\": ${petId}, \"name\": \"Rex\", \"status\": \"sold\", \"photoUrls\": [\"string\"] }. " +
                "Headers: { \"Content-Type\": \"application/json\" }.",
                List.of("petId"),
                "Request sent. Status: 200. Response JSON contains the updated pet status."));

        // Delete Pet (DELETE /pet/{petId})
        steps.add(new TestStep("Delete the pet by sending a DELETE request to '/pet/${petId}'",
                List.of("${petId}"),
                "Request sent. Status: 200"));

        // Verify that the pet record doesn't exist anymore
        steps.add(new TestStep("Send a GET request to '/pet/${petId}'.",
                List.of("${petId}"),
                "Request sent. Status: 404"));

        TestCase testCase = new TestCase("Swagger Petstore End-to-End Flow", List.of(precondition), steps);

        // When
        TestExecutionResult actualResult;
        try (BeanScope scope = BeanScope.builder().build()) {
            try (BeanScope requestScope = scope.get(ApiAgentRequestScopeFactory.class).create(StreamingEventEmitter.NOOP)) {
                actualResult = requestScope.get(ApiTestAgent.class).executeTestCase(OBJECT_MAPPER.writeValueAsString(testCase));
            }
        }

        // Then
        assertThat(actualResult.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.PASSED);
    }
}
