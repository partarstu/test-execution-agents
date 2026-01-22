package org.tarik.ta.dto;

import dev.langchain4j.model.output.structured.Description;

@Description("Result of the pause operation")
public record CountdownResult(
        @Description("Defines if the execution of the test case should go on (true), or should be halted (false)")
        boolean proceed) {
}
