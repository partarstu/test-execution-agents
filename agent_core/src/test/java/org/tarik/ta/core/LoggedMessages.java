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
package org.tarik.ta.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class LoggedMessages {
    private LoggedMessages() {
    }

    /** Runs the action and returns the formatted messages the logger of the given class wrote meanwhile, DEBUG level included. */
    public static List<String> loggedMessagesOf(Class<?> loggingClass, Runnable action) {
        var logger = (Logger) LoggerFactory.getLogger(loggingClass);
        var appender = new ListAppender<ILoggingEvent>();
        var previousLevel = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
