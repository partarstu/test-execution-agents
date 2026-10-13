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
package org.tarik.ta.core.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds the test data values treated as secrets and swaps them for {@code {{secret:<key>}}} placeholders, which only the tool
 * consuming a value resolves back. It is process-wide, holds one run at a time and is reset by the single-threaded A2A executor,
 * because the Logback converter redacting the agent's own logs has no access to the request scope.
 */
public final class SecretRegistry {
    private static final SecretRegistry GLOBAL = new SecretRegistry();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{secret:([^}]+)}}");
    private static final int MIN_MASKED_LENGTH = 4;

    private final Map<String, String> valuesByKey = new ConcurrentHashMap<>();
    /** Matches nothing until a run configures the real convention, so a stray key is never treated as a secret. */
    private volatile Pattern sensitiveKeyPattern = Pattern.compile("(?!)");

    private SecretRegistry() {
    }

    /** The registry of the current process. */
    public static SecretRegistry global() {
        return GLOBAL;
    }

    /**
     * The placeholder which stands for the value of the given key everywhere outside the consuming tool.
     */
    public static String placeholderFor(@NotNull String key) {
        return "{{secret:%s}}".formatted(key);
    }

    /**
     * Drops everything of the previous run and applies the key convention of the current one.
     */
    public void reset(@NotNull Pattern sensitiveKeyPattern) {
        this.sensitiveKeyPattern = sensitiveKeyPattern;
        valuesByKey.clear();
    }

    public boolean isSensitiveKey(@Nullable String key) {
        return key != null && sensitiveKeyPattern.matcher(key).matches();
    }

    /**
     * Registers the value of a sensitive key and returns the placeholder to store and show in its place, or empty when the key is
     * not sensitive or the value is too short to mask meaningfully.
     */
    public Optional<String> register(@Nullable String key, @Nullable Object value) {
        if (!isSensitiveKey(key) || !(value instanceof String text) || text.length() < MIN_MASKED_LENGTH) {
            return Optional.empty();
        }
        valuesByKey.put(key, text);
        return Optional.of(placeholderFor(key));
    }

    /**
     * Replaces every placeholder of a registered key by its real value, only at the boundary that consumes it, such as typing the
     * text or sending the HTTP request.
     */
    public String resolve(@Nullable String text) {
        if (text == null || text.isEmpty() || valuesByKey.isEmpty()) {
            return text;
        }
        var matcher = PLACEHOLDER.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            var value = valuesByKey.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Replaces every registered secret value in the text by its placeholder, longest values first so that a value containing
     * another one is not mangled.
     */
    public String redact(@Nullable String text) {
        if (text == null || text.isEmpty() || valuesByKey.isEmpty()) {
            return text;
        }
        var result = text;
        for (Map.Entry<String, String> entry : entriesLongestValueFirst()) {
            result = result.replace(entry.getValue(), placeholderFor(entry.getKey()));
        }
        return result;
    }

    private List<Map.Entry<String, String>> entriesLongestValueFirst() {
        var entries = new ArrayList<>(valuesByKey.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getValue().length()).reversed());
        return entries;
    }
}
