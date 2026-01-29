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
ARG SOURCE_JAR_PATH=target/ui-test-execution-agent-1.0.0-SNAPSHOT.jar
ARG DESTINATION_JAR_PATH=${APP_DIR}/app.jar
ARG SOURCE_START_SCRIPT=deployment/agent_startup.sh
ARG DESTINATION_START_SCRIPT=${APP_DIR}/agent_startup.sh
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

# --- Application Setup ---
# Copy Java application JAR file into the container.
COPY ${SOURCE_JAR_PATH} ${DESTINATION_JAR_PATH}
RUN mkdir -p ${APP_LOG_DIR}
RUN chmod 777 -R ${APP_LOG_DIR}
RUN mkdir -p ${APP_SCREENSHOTS_DIR}
RUN chmod 777 -R ${APP_SCREENSHOTS_DIR}

# This script will be executed after the main VNC and desktop services are running.
COPY ${SOURCE_START_SCRIPT} ${DESTINATION_START_SCRIPT}
RUN chmod 777 ${DESTINATION_START_SCRIPT}

# --- Environment Variables ---
ENV APP_JAR_PATH=${DESTINATION_JAR_PATH}
ENV DISPLAY=:1
ENV BROWSER_COMMAND="google-chrome-stable"
ENV SCREENSHOTS_SAVE_FOLDER="${APP_SCREENSHOTS_DIR}"
ENV LOG_DIR=${APP_LOG_DIR}
ENV DEPLOYMENT_ENV="local"
ENV SCREEN_RECORDING_FOLDER="${APP_DIR}/log/videos"

# Screenshot and bounding box settings
ENV BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS="5000"
ENV BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS="10"
ENV BOUNDING_BOX_ALREADY_NORMALIZED="true"

# Debug and logging settings
ENV DEBUG_MODE="true"
ENV LOG_LEVEL="DEBUG"
ENV logging.level.dev.langchain4j="INFO"
ENV UNATTENDED_MODE="true"

# Element Bounding Box Agent
ENV ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME="gemini-3-flash-preview"
ENV ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER="google"
ENV ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION="v1.0.0"

# Element Selection Agent
ENV ELEMENT_SELECTION_AGENT_MODEL_NAME="gemini-3-flash-preview"
ENV ELEMENT_SELECTION_AGENT_MODEL_PROVIDER="google"
ENV ELEMENT_SELECTION_AGENT_PROMPT_VERSION="v1.0.0"

# Page Description Agent
ENV PAGE_DESCRIPTION_AGENT_MODEL_NAME="gemini-3-flash-preview"
ENV PAGE_DESCRIPTION_AGENT_MODEL_PROVIDER="google"
ENV PAGE_DESCRIPTION_AGENT_PROMPT_VERSION="v1.0.0"

# Precondition Agent
ENV PRECONDITION_AGENT_MODEL_NAME="gemini-3-flash-preview"
ENV PRECONDITION_AGENT_MODEL_PROVIDER="google"
ENV PRECONDITION_AGENT_PROMPT_VERSION="v1.0.0"

# Precondition Verification Agent
ENV PRECONDITION_VERIFICATION_AGENT_MODEL_NAME="gemini-3-flash-preview"
ENV PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER="google"
ENV PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION="v1.0.0"

# Test Case Extraction Agent
ENV TEST_CASE_EXTRACTION_AGENT_MODEL_NAME="gemini-3-flash-preview"
ENV TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER="google"
ENV TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION="v1.0.0"

# Test Step Action Agent
ENV TEST_STEP_ACTION_AGENT_MODEL_NAME="gemini-3-flash-preview"
ENV TEST_STEP_ACTION_AGENT_MODEL_PROVIDER="google"
ENV TEST_STEP_ACTION_AGENT_PROMPT_VERSION="v1.0.0"

# Test Step Verification Agent
ENV TEST_STEP_VERIFICATION_AGENT_MODEL_NAME="gemini-3-flash-preview"
ENV TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER="google"
ENV TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION="v1.0.0"

# UI Element Description Agent
ENV UI_ELEMENT_DESCRIPTION_AGENT_MODEL_NAME="gemini-3-flash-preview"
ENV UI_ELEMENT_DESCRIPTION_AGENT_MODEL_PROVIDER="google"
ENV UI_ELEMENT_DESCRIPTION_AGENT_PROMPT_VERSION="v1.0.0"

# UI State Check Agent
ENV UI_STATE_CHECK_AGENT_MODEL_NAME="gemini-3-flash-preview"
ENV UI_STATE_CHECK_AGENT_MODEL_PROVIDER="google"
ENV UI_STATE_CHECK_AGENT_PROMPT_VERSION="v1.0.0"

# API Keys and Endpoints
# GOOGLE_API_KEY passed at runtime from host environment
# GROQ_API_KEY passed at runtime from host environment
# Vector DB (VECTOR_DB_URL and VECTOR_DB_KEY passed at runtime from host environment)
ENV GOOGLE_LOCATION=""
ENV GOOGLE_PROJECT=""
ENV GROQ_ENDPOINT="https://api.groq.com/openai/v1"