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
package org.tarik.ta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

        // Step 1: Load Data
        String testDataPath = Paths.get("src", "test", "resources", "test-data.json").toAbsolutePath().toString();
        var precondition = "Pet data is loaded from '" + testDataPath + "' file into variable as 'target pet'";

        // Step 2: Create Pet (POST /pet)
        steps.add(new TestStep("Create a new pet by sending a POST request to '/pet'. Use the first item from the " +
                "loaded pet list items as the body. Set 'Content-Type' header to 'application/json'.",
                List.of("${petList}"),
                "Request is sent. Status: 200."));

        // Step 3: Extract ID
        steps.add(new TestStep("Store the value of the ID from the previous response JSON into the test context under the " +
                "variable 'petId'.",
                List.of(),
                "Variable successfully stored in the test context."));

        // Step 4: Upload Image (POST /pet/{petId}/uploadImage)
        String imagePath = Paths.get("src", "test", "resources", "pet-image.png").toAbsolutePath().toString();
        steps.add(new TestStep("Upload the file '" + imagePath + "' to '/pet/${petId}/uploadImage'. Use multipart name 'file'.",
                List.of("${petId}"),
                "File uploaded. Status: 200"));

        // Step 5: Get Pet (GET /pet/{petId})
        steps.add(new TestStep("Send a GET request to '/pet/${petId}' to retrieve the pet details.",
                List.of("petId"),
                "Request sent. Status: 200"));

        // Step 6: Validate OpenAPI
        steps.add(new TestStep("Validate the last response against the OpenAPI spec at '" + OPENAPI_SPEC_URL + "'.",
                List.of(),
                "OpenAPI validation passed."));

        // Step 7: Update Pet (PUT /pet)
        steps.add(new TestStep("Update the pet status to 'sold' by sending a PUT request to '/pet'. " +
                "Body: { \"id\": ${petId}, \"name\": \"Rex\", \"status\": \"sold\", \"photoUrls\": [\"string\"] }. " +
                "Headers: { \"Content-Type\": \"application/json\" }.",
                List.of("petId"),
                "Request sent. Status: 200"));

        // Step 8: Verify Update
        steps.add(new TestStep("Assert that the JSON path 'status' in the last response matches 'sold'.",
                List.of(),
                "Assertion passed: status == sold"));

        // Step 9: Delete Pet (DELETE /pet/{petId})
        steps.add(new TestStep("Delete the pet by sending a DELETE request to '/pet/${petId}'. " +
                "Auth Type: API_KEY, Value: 'api_key=special-key', Location: HEADER.",
                List.of("petId"),
                "Request sent. Status: 200"));

        // Step 10: Verify Deletion
        steps.add(new TestStep("Send a GET request to '/pet/${petId}'.",
                List.of("petId"),
                "Request sent. Status: 404"));

        TestCase testCase = new TestCase("Swagger Petstore End-to-End Flow", List.of(precondition), steps);

        // When
        TestExecutionResult actualResult = ApiTestAgent.executeTestCase(OBJECT_MAPPER.writeValueAsString(testCase));

        // Then
        assertThat(actualResult.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.PASSED);
    }
}

