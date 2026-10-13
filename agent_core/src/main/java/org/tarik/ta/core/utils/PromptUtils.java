/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.regex.Matcher.quoteReplacement;

public class PromptUtils {
    private static final Logger LOG = LoggerFactory.getLogger(PromptUtils.class);
    private static final Pattern INCLUDE_LINE = Pattern.compile("^\\{\\{>\\s*([^\\s}]+)\\s*}}$", Pattern.MULTILINE);

    /**
     * Loads the prompt, replacing each {@code {{> <agent path>/<version>/<file name>}}} line with the loaded content of that file.
     */
    public static String loadSystemPrompt(String agentPath, String version, String fileName) {
        return loadPrompt(agentPath + "/" + version + "/" + fileName, List.of());
    }

    private static String loadPrompt(String promptPath, List<String> includingPrompts) {
        var includeChain = Stream.concat(includingPrompts.stream(), Stream.of(promptPath)).toList();
        if (includingPrompts.contains(promptPath)) {
            throw new IllegalArgumentException("Prompt include cycle: " + String.join(" -> ", includeChain));
        }
        String path = "prompt_templates/system/agents/" + promptPath;
        LOG.info("Loading system prompt from: {}", path);
        try (InputStream inputStream = PromptUtils.class.getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Prompt file not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return resolveIncludes(reader.lines().collect(Collectors.joining(System.lineSeparator())), includeChain);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt from " + path, e);
        }
    }

    private static String resolveIncludes(String prompt, List<String> includeChain) {
        return INCLUDE_LINE.matcher(prompt).replaceAll(include -> quoteReplacement(loadPrompt(include.group(1), includeChain)));
    }
}
