package org.tarik.ta.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.opencsv.bean.CsvToBeanBuilder;
import dev.langchain4j.agent.tool.Tool;
import org.tarik.ta.context.ApiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.util.List;

public class ApiDataTools {
    private static final Logger LOG = LoggerFactory.getLogger(ApiDataTools.class);
    private final ApiContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiDataTools(ApiContext context) {
        this.context = context;
    }

    @Tool("Loads JSON data from a file into a list of objects of the specified class, and stores it in a context variable.")
    public String loadJsonData(String filePath, String className, String variableName) {
        try {
            Class<?> clazz = Class.forName(className);
            CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
            List<?> data = objectMapper.readValue(new File(filePath), listType);

            context.setVariable(variableName, data);
            return "Loaded " + data.size() + " items into variable '" + variableName + "'";
        } catch (Exception e) {
            LOG.error("Error loading JSON data from {}", filePath, e);
            return "Error loading JSON data: " + e.getMessage();
        }
    }

    @Tool("Loads CSV data from a file into a list of objects of the specified class, and stores it in a context variable.")
    public String loadCsvData(String filePath, String className, String variableName) {
        try {
            Class<?> clazz = Class.forName(className);
            List<?> data = new CsvToBeanBuilder<>(new FileReader(filePath))
                    .withType(clazz)
                    .build()
                    .parse();

            context.setVariable(variableName, data);
            return "Loaded " + data.size() + " items into variable '" + variableName + "'";
        } catch (Exception e) {
            LOG.error("Error loading CSV data from {}", filePath, e);
            return "Error loading CSV data: " + e.getMessage();
        }
    }
}
