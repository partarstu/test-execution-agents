/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.utils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
