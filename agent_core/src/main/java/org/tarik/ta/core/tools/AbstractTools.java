package org.tarik.ta.core.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import static java.lang.String.format;
import static org.tarik.ta.core.error.ErrorCategory.UNKNOWN;

public class AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTools.class);

    protected RuntimeException rethrowAsToolException(Exception e, String operationContext) {
        if (e instanceof ToolExecutionException toolExecutionException) {
            return toolExecutionException;
        } else {
            LOG.error("Error during {}", operationContext, e);
            return new ToolExecutionException(format("Error while %s: %s", operationContext, e.getMessage()), UNKNOWN);
        }
    }
}
