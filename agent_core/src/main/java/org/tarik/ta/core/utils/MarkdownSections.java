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

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;

/**
 * Renders the Markdown sections of the LLM user messages, leaving out every section without data. Heading lines inside the data
 * are escaped, so data never opens a section of its own.
 */
public final class MarkdownSections {
    public static final int SECTION_LEVEL = 2;
    public static final int SUBSECTION_LEVEL = 3;
    private static final Pattern HEADING_LINE = Pattern.compile("^( {0,3})(#{1,6})(?=[ \\t]|$)", Pattern.MULTILINE);
    private static final Pattern LINE_BREAKS = Pattern.compile("\\R+");
    private static final String SECTION_SEPARATOR = "\n\n";
    private static final String LINE_SEPARATOR = "\n";
    private static final String LIST_ITEM_INDENT = "  ";

    private MarkdownSections() {
    }

    public static String heading(int level, @NotNull String title) {
        return "%s %s".formatted("#".repeat(level), singleLine(title));
    }

    /** Returns the heading with the text on the lines below it, or an empty string when the text is blank. */
    public static String section(int level, @NotNull String title, @Nullable String text) {
        return isBlank(text) ? "" : "%s\n%s".formatted(heading(level, title), escapeHeadings(text.strip()));
    }

    /** Returns the heading followed by the non-blank child sections, or an empty string when every child is blank. */
    public static String parentSection(int level, @NotNull String title, @NotNull String... children) {
        var body = joinSections(children);
        return body.isEmpty() ? "" : joinSections(heading(level, title), body);
    }

    /**
     * Returns {@code "- <label>: <value>"}, with a multi-line value indented on the lines below the label instead, or an empty
     * string when the value is blank.
     */
    public static String listItem(@NotNull String label, @Nullable Object value) {
        var text = Objects.toString(value, "");
        if (isBlank(text)) {
            return "";
        }
        var trimmed = text.strip();
        var item = trimmed.contains(LINE_SEPARATOR)
                ? "- %s:%s%s".formatted(singleLine(label), LINE_SEPARATOR, indent(trimmed))
                : "- %s: %s".formatted(singleLine(label), trimmed);
        return escapeHeadings(item);
    }

    /** Returns {@code "- <label>:"} with the entries as a nested list below it, or an empty string when there are no entries. */
    public static String nestedList(@NotNull String label, @NotNull Collection<String> entries) {
        var list = bulletList(entries);
        return list.isEmpty() ? "" : "- %s:%s%s".formatted(singleLine(label), LINE_SEPARATOR, indent(list));
    }

    /** Returns one {@code "- <entry>"} item per non-blank entry, the further lines of a multi-line entry indented below it. */
    public static String bulletList(@NotNull Collection<String> entries) {
        return entries.stream()
                .filter(CommonUtils::isNotBlank)
                .map(entry -> escapeHeadings("- %s".formatted(indent(entry.strip()).substring(LIST_ITEM_INDENT.length()))))
                .collect(joining(LINE_SEPARATOR));
    }

    /** Joins the non-blank lines, one per line. */
    public static String joinLines(@NotNull String... lines) {
        return joinNonBlank(lines, LINE_SEPARATOR);
    }

    /** Joins the non-blank sections, each separated from the next by an empty line. */
    public static String joinSections(@NotNull String... sections) {
        return joinNonBlank(sections, SECTION_SEPARATOR);
    }

    /**
     * Wraps the text for a {@code @UserMessage List<Content>} agent parameter, because LangChain4j sends a lone {@code @UserMessage
     * TextContent} argument twice, and parses a {@code String} one as a template.
     */
    public static List<Content> userMessageContents(@NotNull String text) {
        return List.of(TextContent.from(text));
    }

    static String escapeHeadings(@NotNull String text) {
        return HEADING_LINE.matcher(text).replaceAll("$1\\\\$2");
    }

    private static String singleLine(String text) {
        return LINE_BREAKS.matcher(text.strip()).replaceAll(" ");
    }

    private static String indent(String text) {
        return text.lines().map(line -> line.isBlank() ? "" : LIST_ITEM_INDENT + line).collect(joining(LINE_SEPARATOR));
    }

    private static String joinNonBlank(String[] parts, String separator) {
        return Arrays.stream(parts).filter(CommonUtils::isNotBlank).map(String::strip).collect(joining(separator));
    }
}
