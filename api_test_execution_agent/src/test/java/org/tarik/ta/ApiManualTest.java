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
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;

import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class ApiManualTest {

    @Test
    void testPetstoreFlow() throws JsonProcessingException {
        // Given
        var steps = new LinkedList<TestStep>();

        // Step 1: Load Data
        String testDataPath = Paths.get("src", "test", "resources", "test-data.json").toAbsolutePath().toString();
        var precondition = "Pet data are loaded from '" + testDataPath + "' file into variable 'petList' using ApiDataTools.loadJsonData.";

        steps.add(new TestStep("Load pet data from '" + testDataPath + "' into variable 'petList' using ApiDataTools.loadJsonData.",
                List.of(),
                "Data is loaded successfully."));

        // Step 2: Create Pet
        steps.add(new TestStep("Create a new pet by sending a POST request to '/pet'. Use the first item from the loaded pet list items " +
                "as the body. Set 'Content-Type' header to 'application/json'.",
                List.of("${petList}"),
                "Request is sent. Status: 200."));

        // Step 3: Extract ID
        steps.add(new TestStep("Extract the 'id' from the response JSON and store it in variable 'petId'.",
                List.of(),
                "Extracted value to variable 'petId'"));

        // Step 4: Upload Image
        String imagePath = Paths.get("src", "test", "resources", "pet-image.png").toAbsolutePath().toString();
        steps.add(new TestStep("Upload the file '" + imagePath + "' to '/pet/${petId}/uploadImage'. Use multipart name 'file'.",
                List.of("petId"),
                "File uploaded. Status: 200"));

        // Step 5: Get Pet (Verify creation)
        steps.add(new TestStep("Send a GET request to '/pet/${petId}' to retrieve the pet details.",
                List.of("petId"),
                "Request sent. Status: 200"));

        // Step 6: Validate OpenAPI
        steps.add(new TestStep("Validate the last response against the OpenAPI spec at 'https://petstore.swagger.io/v2/swagger.json'.",
                List.of(),
                "OpenAPI validation passed."));

        // Step 7: Update Pet (PUT)
        steps.add(new TestStep("Update the pet status to 'sold' by sending a PUT request to '/pet'. " +
                "Body: { \"id\": ${petId}, \"name\": \"Rex\", \"status\": \"sold\", \"photoUrls\": [\"string\"] }. Headers: { \"Content-Type\": \"application/json\" }.",
                List.of("petId"),
                "Request sent. Status: 200"));

        // Step 8: Verify Update
        steps.add(new TestStep("Assert that the JSON path 'status' in the last response matches 'sold'.",
                List.of(),
                "Assertion passed: status == sold"));

        // Step 9: Delete Pet
        steps.add(new TestStep(
                "Delete the pet by sending a DELETE request to '/pet/${petId}'. " +
                        "Auth Type: API_KEY, Value: 'api_key=special-key', Location: HEADER.",
                List.of("petId"),
                "Request sent. Status: 200"));

        // Step 10: Verify Deletion
        steps.add(new TestStep("Send a GET request to '/pet/${petId}'.",
                List.of("petId"),
                "Request sent. Status: 404"));

        TestCase testCase = new TestCase("Swagger Petstore End-to-End Flow",
                List.of("The Swagger Petstore API is available at configured base URI"),
                steps);

        // When
        TestExecutionResult actualResult = ApiTestAgent
                .executeTestCase(new ObjectMapper().writeValueAsString(testCase));

        // Then
        assertThat(actualResult.testExecutionStatus())
                .isEqualTo(TestExecutionResult.TestExecutionStatus.PASSED);
    }
}
