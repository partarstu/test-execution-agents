#
# Copyright © 2025 Taras Paruta (partarstu@gmail.com)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Use the custom VNC + Chrome base image
# Build the base image first: docker build -t ui-testing-agent-base -f deployment/Dockerfile.base deployment/
FROM ui-testing-agent-base:latest

ARG APP_DIR=/app
ARG APP_LOG_DIR=${APP_DIR}/log
ARG APP_SCREENSHOTS_DIR=${APP_DIR}/screenshots
ARG SOURCE_JAR_PATH=ui_test_execution_agent/target/ui-test-execution-agent-1.0.0-SNAPSHOT.jar
ARG DESTINATION_JAR_PATH=${APP_DIR}/app.jar
ARG SOURCE_START_SCRIPT=ui_test_execution_agent/deployment/agent_startup.sh
ARG DESTINATION_START_SCRIPT=${APP_DIR}/agent_startup.sh
ARG SOURCE_START_WEBSOCKIFY_SSL_SCRIPT=ui_test_execution_agent/deployment/cloud/start_websockify_ssl.sh
ARG DESTINATION_START_WEBSOCKIFY_SSL_SCRIPT=${APP_DIR}/start_websockify_ssl.sh
ARG JAVA_VERSION=25

# Switch to root user to install software
USER 0

# --- Java Installation ---
# Install a Java Runtime Environment (JRE).
# The base image is Ubuntu, so we can use apt-get.
# '-y' answers yes to any prompts.
# '&&' chains commands together.
# 'apt-get clean' removes downloaded package files to keep the image small.
RUN apt-get update
RUN wget https://download.oracle.com/java/${JAVA_VERSION}/latest/jdk-${JAVA_VERSION}_linux-x64_bin.deb
RUN dpkg -i jdk-${JAVA_VERSION}_linux-x64_bin.deb
RUN java --version
RUN update-alternatives --list java
RUN apt-get install --reinstall libgtk2.0-0 -y
RUN apt-get clean

# --- SSL Certificate Generation for noVNC ---
# Install openssl for certificate generation
RUN apt-get update && apt-get install -y openssl lsof

# Create a directory for SSL certificates
RUN mkdir -p /etc/ssl/novnc

# Generate a self-signed certificate and key
# For production, you should use a proper CA-signed certificate
RUN openssl req -x509 -nodes -newkey rsa:2048 -keyout /etc/ssl/novnc/novnc.key -out /etc/ssl/novnc/novnc.crt -days 365 -subj "/CN=novnc"

# Copy the custom websockify startup script
COPY ${SOURCE_START_WEBSOCKIFY_SSL_SCRIPT} ${DESTINATION_START_WEBSOCKIFY_SSL_SCRIPT}
RUN chmod +x ${DESTINATION_START_WEBSOCKIFY_SSL_SCRIPT}

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