/*
 * agent-core - ${project.description}
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
package org.tarik.ta.core.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.opencsv.CSVReader;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.core.config.scopes.BaseAgentRequestScope;
import jakarta.inject.Inject;

import java.io.File;
import java.io.FileReader;
import java.util.*;

import static java.lang.Math.min;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import java.nio.file.Files;

@BaseAgentRequestScope
public class TestContextDataTools extends AbstractTools {
    private final TestExecutionContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public TestContextDataTools(TestExecutionContext context) {
        this.context = context;
    }

    @Tool("Loads data from a file as a JSON string, validating it, and stores it in the test context.")
    public String loadJsonData(
            @P("Path to the JSON file") String filePath,
            @P("Variable name under which the data will be stored in the test context") String variableName) {
        if (isBlank(filePath)) {
            throw new ToolExecutionException("File path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (isBlank(variableName)) {
            throw new ToolExecutionException("Variable name cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new ToolExecutionException("File does not exist: " + filePath, TRANSIENT_TOOL_ERROR);
            }

            String content = Files.readString(file.toPath());

            if (!content.trim().isEmpty()) {
                objectMapper.readTree(content);
            }

            context.addSharedData(variableName, content);
            return "Loaded JSON data from " + filePath + " into shared variable '" + variableName + "'";
        } catch (Exception e) {
            throw rethrowAsToolException(e, "loading JSON data from " + filePath);
        }
    }

    @Tool("Loads CSV data from a file into a list of maps (dictionaries) where keys are column names, and stores it in the test context.")
    public String loadCsvData(
            @P("Path to the CSV file") String filePath,
            @P("Variable name to store data") String variableName) {
        if (isBlank(filePath)) {
            throw new ToolExecutionException("File path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (isBlank(variableName)) {
            throw new ToolExecutionException("Variable name cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            List<String[]> allRows = reader.readAll();
            if (allRows.isEmpty()) {
                throw new ToolExecutionException("CSV file is empty: " + filePath, TRANSIENT_TOOL_ERROR);
            }

            String[] header = allRows.getFirst();
            if (header == null || header.length == 0) {
                throw new ToolExecutionException("CSV file contains no valid header: " + filePath, TRANSIENT_TOOL_ERROR);
            }
            if (allRows.size() < 2) {
                throw new ToolExecutionException("CSV file must contain at least one data row: " + filePath, TRANSIENT_TOOL_ERROR);
            }

            List<String[]> dataRows = allRows.subList(1, allRows.size());
            List<Map<String, String>> result = new ArrayList<>();
            for (String[] row : dataRows) {
                Map<String, String> rowMap = new LinkedHashMap<>();
                int length = min(header.length, row.length);
                for (int i = 0; i < length; i++) {
                    rowMap.put(header[i], row[i]);
                }
                result.add(rowMap);
            }

            context.addSharedData(variableName, result);
            return "Loaded " + result.size() + " items into shared variable '" + variableName + "'";
        } catch (Exception e) {
            throw rethrowAsToolException(e, "loading CSV data from " + filePath);
        }
    }

    @Tool("Stores a new variable into a test context.")
    public String storeVariableIntoContext(
            @P("Variable name to store") String variableName,
            @P("Value for the variable to store") String variableValue) {
        context.addSharedData(variableName, variableValue);
        return "Added variable '%s' with value '%s' to the test context.".formatted(variableName, variableValue);
    }
}
