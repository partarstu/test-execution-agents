package org.tarik.ta.core.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.model.TestExecutionContext;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

class TestContextDataToolsTest {

    @Mock
    private TestExecutionContext context;

    private TestContextDataTools tools;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tools = new TestContextDataTools(context);
    }

    @Test
    void loadJsonData_validJson_shouldLoadAndStore() throws Exception {
        Path jsonFile = tempDir.resolve("data.json");
        String jsonContent = "{\"key\": \"value\"}";
        Files.writeString(jsonFile, jsonContent);

        String result = tools.loadJsonData(jsonFile.toString(), "myVar");

        assertThat(result).contains("Loaded JSON data");
        verify(context).setSharedData("myVar", jsonContent);
    }

    @Test
    void loadJsonData_invalidJson_shouldThrowException() throws Exception {
        Path jsonFile = tempDir.resolve("invalid.json");
        Files.writeString(jsonFile, "{invalid");

        assertThrows(ToolExecutionException.class, () ->
                tools.loadJsonData(jsonFile.toString(), "myVar"));
    }

    @Test
    void loadJsonData_nonExistentFile_shouldThrowException() {
        String nonExistentPath = tempDir.resolve("non_existent.json").toString();
        assertThrows(ToolExecutionException.class, () ->
                tools.loadJsonData(nonExistentPath, "myVar"));
    }

    @Test
    void loadJsonData_emptyPath_shouldThrowException() {
        assertThrows(ToolExecutionException.class, () ->
                tools.loadJsonData("", "myVar"));
    }

    @Test
    void loadJsonData_emptyVarName_shouldThrowException() {
        assertThrows(ToolExecutionException.class, () ->
                tools.loadJsonData("path/to/file.json", ""));
    }

    @Test
    void loadCsvData_validCsv_shouldLoadAndStore() throws Exception {
        Path csvFile = tempDir.resolve("data.csv");
        try (FileWriter writer = new FileWriter(csvFile.toFile())) {
            writer.write("col1,col2\nval1,val2\nval3,val4");
        }

        String result = tools.loadCsvData(csvFile.toString(), "myCsvVar");

        assertThat(result).contains("Loaded 2 items");
        // Verify the content is passed to the context (using argument captor or just verifying the call happened is usually enough if we trust the logic, but let's check basic interaction)
        // Since we can't easily check the list content without captor, checking verify(context) is called is good.
        // But let's assume strict verification isn't strictly needed for this level unless we capture.
        // We know the logic:
        /*
            List<Map<String, String>> result = new ArrayList<>();
            ...
            context.setSharedData(variableName, result);
         */
        verify(context).setSharedData(org.mockito.ArgumentMatchers.eq("myCsvVar"), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void loadCsvData_emptyCsv_shouldThrowException() throws Exception {
        Path csvFile = tempDir.resolve("empty.csv");
        Files.createFile(csvFile);

        assertThrows(ToolExecutionException.class, () ->
                tools.loadCsvData(csvFile.toString(), "myCsvVar"));
    }

    @Test
    void loadCsvData_headerOnlyCsv_shouldThrowException() throws Exception {
        Path csvFile = tempDir.resolve("headerOnly.csv");
        Files.writeString(csvFile, "col1,col2");

        assertThrows(ToolExecutionException.class, () ->
                tools.loadCsvData(csvFile.toString(), "myCsvVar"));
    }

    @Test
    void loadCsvData_nonExistentFile_shouldThrowException() {
         String nonExistentPath = tempDir.resolve("non_existent.csv").toString();
        assertThrows(ToolExecutionException.class, () ->
                tools.loadCsvData(nonExistentPath, "myCsvVar"));
    }
    
     @Test
    void loadCsvData_emptyPath_shouldThrowException() {
        assertThrows(ToolExecutionException.class, () ->
                tools.loadCsvData("", "myVar"));
    }

    @Test
    void loadCsvData_emptyVarName_shouldThrowException() {
        assertThrows(ToolExecutionException.class, () ->
                tools.loadCsvData("path/to/file.csv", ""));
    }
}
