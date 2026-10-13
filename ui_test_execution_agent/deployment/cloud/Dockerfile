#
# ui-test-execution-agent - Agent specializing in execution of UI tests.
# Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#

# Use the custom VNC + Chrome base image
# Build the base image first: docker build -t ui-testing-agent-base -f deployment/Dockerfile.base deployment/
FROM ui-testing-agent-base:latest

ARG APP_DIR=/app
ARG APP_LOG_DIR=${APP_DIR}/log
ARG APP_SCREENSHOTS_DIR=${APP_DIR}/screenshots
ARG SOURCE_JAR_PATH=ui_test_execution_agent/target/ui-test-execution-agent-3.1.0-SNAPSHOT.jar
ARG DESTINATION_JAR_PATH=${APP_DIR}/app.jar
ARG SOURCE_START_SCRIPT=ui_test_execution_agent/deployment/agent_startup.sh
ARG DESTINATION_START_SCRIPT=${APP_DIR}/agent_startup.sh
ARG JAVA_VERSION=27

# Switch to root user to install software
USER 0

# --- Java Installation ---
# Install a Java Runtime Environment (JRE).
# The base image is Ubuntu, so we can use apt-get.
# '-y' answers yes to any prompts.
# '&&' chains commands together.
# 'apt-get clean' removes downloaded package files to keep the image small.
RUN wget -qO - https://apt.corretto.aws/corretto.key | gpg --dearmor -o /usr/share/keyrings/corretto-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/corretto-keyring.gpg] https://apt.corretto.aws stable main" > /etc/apt/sources.list.d/corretto.list \
    && apt-get update \
    && apt-get install -y java-${JAVA_VERSION}-amazon-corretto-jdk \
    && apt-get install --reinstall libgtk2.0-0 -y \
    && apt-get clean
RUN java --version
RUN update-alternatives --list java

# --- Application Setup ---
# Copy your Java application JAR file into the container, create logs folder with corresponding access rights.
COPY ${SOURCE_JAR_PATH} ${DESTINATION_JAR_PATH}
RUN mkdir -p ${APP_LOG_DIR}
RUN chmod 777 -R ${APP_LOG_DIR}
RUN mkdir -p ${APP_SCREENSHOTS_DIR}
RUN chmod 777 -R ${APP_SCREENSHOTS_DIR}

# This script will be executed after the main VNC and desktop services are running.
COPY ${SOURCE_START_SCRIPT} ${DESTINATION_START_SCRIPT}
RUN chmod 777 ${DESTINATION_START_SCRIPT}

# --- Environment Variables ---
# Only static/essential environment variables are defined here.
# All agent-specific configuration is passed at runtime via deployment scripts.
ENV APP_JAR_PATH=${DESTINATION_JAR_PATH}
ENV DISPLAY=:1
ENV BROWSER_COMMAND="google-chrome-stable"
ENV SCREENSHOTS_SAVE_FOLDER="${APP_SCREENSHOTS_DIR}"
ENV LOG_DIR=${APP_LOG_DIR}
ENV DEPLOYMENT_ENV="cloud"
ENV SCREEN_RECORDING_FOLDER="${APP_DIR}/log/videos"