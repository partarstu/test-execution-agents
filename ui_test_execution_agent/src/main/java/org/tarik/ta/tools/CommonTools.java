/*
 * ui-test-execution-agent - ${project.description}
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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Singleton;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.awt.Desktop.getDesktop;
import static java.awt.Desktop.isDesktopSupported;
import static org.tarik.ta.core.error.ErrorCategory.*;
import static org.tarik.ta.core.utils.CommonUtils.*;

@Singleton
public class CommonTools extends UiAbstractTools {
    private static final int BROWSER_OPEN_TIME_SECONDS = 1;
    private static final Logger LOG = LoggerFactory.getLogger(CommonTools.class);
    private static final String HTTP_PROTOCOL = "http://";
    private static final String OS_NAME_SYS_PROPERTY = "os.name";
    private static final String HTTPS_PROTOCOL = "https://";
    private static Process browserProcess;
    private static final Object LOCK = new Object();

    public CommonTools(UiStateCheckAgent uiStateCheckAgent) {
        super(uiStateCheckAgent);
    }

    @Tool(value = "Waits the specified amount of seconds. Use this tool when you need to wait after some action.")
    public void waitSeconds(@P(value = "The specific amount of seconds to wait.") int secondsAmount) {
        try {
            sleepSeconds(secondsAmount);
        } catch (Exception e) {
            throw new ToolExecutionException("Failed to wait for " + secondsAmount + " seconds: " + e.getMessage(), UNKNOWN);
        }
    }

    @Tool(value = "Opens the default browser with the specified URL. Use this tool to navigate to a web page.")
    public void openBrowser(@P(value = "The URL to open in the browser.") String url) {
        synchronized (LOCK) {
            if (isBlank(url)) {
                throw new ToolExecutionException("URL must be provided", TRANSIENT_TOOL_ERROR);
            }

            String sanitizedUrl = url;
            if (!sanitizedUrl.toLowerCase().startsWith(HTTP_PROTOCOL) && !sanitizedUrl.toLowerCase().startsWith(HTTPS_PROTOCOL)) {
                LOG.warn("Provided URL '{}' doesn't have the protocol defined, using HTTP as the default one", sanitizedUrl);
                sanitizedUrl = HTTP_PROTOCOL + sanitizedUrl;
            }

            URL finalUrl;
            try {
                finalUrl = URI.create(sanitizedUrl).toURL();
            } catch (MalformedURLException e) {
                throw new ToolExecutionException("Invalid URL format: " + e.getMessage(), TRANSIENT_TOOL_ERROR);
            }

            try {
                closeBrowser();
                String os = System.getProperty(OS_NAME_SYS_PROPERTY).toLowerCase();
                if (os.contains("linux")) {
                    String[] command = buildBrowserStartupCommand(os, finalUrl.toString());
                    LOG.debug("Executing command: {}", String.join(" ", command));
                    browserProcess = new ProcessBuilder(command).start();
                    if (!browserProcess.isAlive()) {
                        var errorMessage = "Failed to open browser. Error: %s\n"
                                .formatted(IOUtils.toString(browserProcess.getErrorStream(), UTF_8));
                        throw new ToolExecutionException(errorMessage, TRANSIENT_TOOL_ERROR);
                    }
                } else if (isDesktopSupported() && getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    getDesktop().browse(finalUrl.toURI());
                } else {
                    LOG.debug(
                            "Java AWT Desktop is not supported on the current OS, falling back to alternative method.");
                    throw new ToolExecutionException("Current OS doesn't support opening a browser.",
                            NON_RETRYABLE_ERROR);
                }
                sleepSeconds(BROWSER_OPEN_TIME_SECONDS);
            } catch (Exception e) {
                throw rethrowAsToolException(e, "opening browser");
            }
        }
    }

    @Tool(value = "Closes the currently open browser instance. Use this tool when you need to close the browser.")
    public void closeBrowser() {
        synchronized (LOCK) {
            try {
                if (browserProcess != null && browserProcess.isAlive()) {
                    browserProcess.destroy();
                    try {
                        browserProcess.waitFor(); // Wait for the process to terminate
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ToolExecutionException("Interrupted while waiting for browser to close", UNKNOWN);
                    }
                }
            } catch (Exception e) {
                throw rethrowAsToolException(e, "closing browser");
            }
        }
    }

    private static String[] buildBrowserStartupCommand(String os, String url) {
        if (os.contains("win")) {
            return new String[]{"cmd.exe", "/c", "start", url};
        } else if (os.contains("mac")) {
            return new String[]{"open", url};
        } else {
            String browserCommand = System.getenv("BROWSER_COMMAND");
            if (browserCommand == null || browserCommand.trim().isEmpty()) {
                browserCommand = "chromium-browser";
            }
            return new String[]{
                    browserCommand,
                    "--no-sandbox",
                    "--test-type",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--start-maximized",
                    "--disable-gpu",
                    "--disable-dev-shm-usage",
                    "--force-device-scale-factor=1",
                    "--in-process-gpu",
                    url
            };
        }
    }
}
