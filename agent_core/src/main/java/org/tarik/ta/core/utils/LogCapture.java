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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.avaje.inject.External;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
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

    private final Logger rootLogger;
    private final StreamingEventEmitter eventEmitter;
    private StreamingAppender appender;

    public LogCapture(@External @NotNull StreamingEventEmitter eventEmitter) {
        this.eventEmitter = eventEmitter;
        this.rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }

    public void start() {
        appender = new StreamingAppender();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @PreDestroy
    public void stop() {
        if (appender != null) {
            rootLogger.detachAppender(appender);
            appender.stop();
        }
    }

    public List<String> getLogs() {
        if (appender == null) {
            return new ArrayList<>();
        }
        return appender.capturedEvents.stream()
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

    /**
     * Captures every log event for the final report and streams each formatted line live through the
     * {@link StreamingEventEmitter}. Streaming a log line may itself produce log events on the same thread, so a
     * re-entrancy guard prevents the forwarding from recursing into itself.
     */
    private final class StreamingAppender extends AppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> capturedEvents = new ArrayList<>();
        private final ThreadLocal<Boolean> forwarding = ThreadLocal.withInitial(() -> false);

        @Override
        protected void append(ILoggingEvent event) {
            capturedEvents.add(event);
            if (forwarding.get()) {
                return;
            }
            forwarding.set(true);
            try {
                eventEmitter.emitLog(formatLogEvent(event));
            } finally {
                forwarding.set(false);
            }
        }
    }
}
