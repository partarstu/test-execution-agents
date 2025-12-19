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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LogCapture {
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
                .map(event -> event.getFormattedMessage())
                .collect(Collectors.toList());
    }
}
