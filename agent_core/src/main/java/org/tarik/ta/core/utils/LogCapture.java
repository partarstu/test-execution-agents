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
package org.tarik.ta.core.utils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.config.scopes.BaseAgentRequestScope;
import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@BaseAgentRequestScope
public class LogCapture {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private ListAppender<ILoggingEvent> listAppender;
    private final Logger rootLogger;

    public LogCapture() {
        this.rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }

    public void start() {
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
    }

    @PreDestroy
    public void stop() {
        if (listAppender != null) {
            rootLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    public List<String> getLogs() {
        if (listAppender == null) {
            return new ArrayList<>();
        }
        return listAppender.list.stream()
                .map(this::formatLogEvent)
                .collect(Collectors.toList());
    }

    private String formatLogEvent(ILoggingEvent event) {
        String timestamp = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp()));
        String level = event.getLevel().toString();
        String loggerName = getShortLoggerName(event.getLoggerName());
        return "%s %-5s %s - %s".formatted(timestamp, level, loggerName, event.getFormattedMessage());
    }

    private String getShortLoggerName(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) {
            return "";
        }
        int lastDotIndex = loggerName.lastIndexOf('.');
        return lastDotIndex >= 0 ? loggerName.substring(lastDotIndex + 1) : loggerName;
    }
}
