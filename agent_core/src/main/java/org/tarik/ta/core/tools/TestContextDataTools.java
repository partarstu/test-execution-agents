package org.tarik.ta.core.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.opencsv.bean.CsvToBeanBuilder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.model.TestExecutionContext;

import java.io.File;
import java.io.FileReader;
import java.util.List;

import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;

public class TestContextDataTools extends AbstractTools {
    private final TestExecutionContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            String content = java.nio.file.Files.readString(file.toPath());

            if (!content.trim().isEmpty()) {
                objectMapper.readTree(content);
            }

            context.setSharedData(variableName, content);
            return "Loaded JSON data from " + filePath + " into shared variable '" + variableName + "'";
        } catch (Exception e) {
            throw rethrowAsToolException(e, "loading JSON data from " + filePath);
        }
    }

    @Tool("Loads CSV data from a file into a list of objects of the specified class, and stores it in a shared context variable.")
    public String loadCsvData(
            @P("Path to the CSV file") String filePath,
            @P("Fully qualified class name") String className,
            @P("Variable name to store data") String variableName) {
        if (isBlank(filePath)) {
            throw new ToolExecutionException("File path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (className == null || className.isBlank()) {
            throw new ToolExecutionException("Class name cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (variableName == null || variableName.isBlank()) {
            throw new ToolExecutionException("Variable name cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        try {
            Class<?> clazz = Class.forName(className);
            List<?> data = new CsvToBeanBuilder<>(new FileReader(filePath))
                    .withType(clazz)
                    .build()
                    .parse();

            context.setSharedData(variableName, data);
            return "Loaded " + data.size() + " items into shared variable '" + variableName + "'";
        } catch (Exception e) {
            throw rethrowAsToolException(e, "loading CSV data from " + filePath);
        }
    }
}
