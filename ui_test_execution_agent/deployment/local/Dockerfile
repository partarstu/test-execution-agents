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
ARG SOURCE_JAR_PATH=target/ui-test-execution-agent-3.1.0-SNAPSHOT.jar
ARG DESTINATION_JAR_PATH=${APP_DIR}/app.jar
ARG SOURCE_START_SCRIPT=deployment/agent_startup.sh
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

# Operator panel: plain HTTP, published on the host's loopback only; PANEL_PASSWORD is passed at runtime
ENV PANEL_HOST="0.0.0.0"
ENV VNC_SOCKET_PATH="/tmp/xvnc.sock"
EXPOSE 8005 8443

# Screenshot and bounding box settings
ENV BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS="5000"
ENV BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS="10"
ENV BOUNDING_BOX_ALREADY_NORMALIZED="true"

# Debug and logging settings
ENV DEBUG_MODE="true"
ENV LOG_LEVEL="DEBUG"
ENV logging.level.dev.langchain4j="INFO"
ENV EXECUTION_MODE="UNATTENDED"

# DB Element Selection Agent
ENV DB_ELEMENT_SELECTION_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER="google"
ENV ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION="v1.0.0"

# Element Bounding Box Agent
ENV ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER="google"
ENV ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION="v1.0.0"

# Element Selection Agent
ENV ELEMENT_SELECTION_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV ELEMENT_SELECTION_AGENT_MODEL_PROVIDER="google"
ENV ELEMENT_SELECTION_AGENT_PROMPT_VERSION="v1.0.0"

# Knowledge Collection Element Resolution Agent
ENV KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER="google"
ENV KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION="v1.0.0"

# Knowledge Suggestion Agent
ENV KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER="google"
ENV KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION="v1.0.0"

# Precondition Agent
ENV PRECONDITION_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV PRECONDITION_AGENT_MODEL_PROVIDER="google"
ENV PRECONDITION_AGENT_PROMPT_VERSION="v1.1.0"

# Precondition Verification Agent
ENV PRECONDITION_VERIFICATION_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER="google"
ENV PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION="v1.1.0"

# Test Case Extraction Agent
ENV TEST_CASE_EXTRACTION_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER="google"
ENV TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION="v1.0.0"

# Test Step Action Agent
ENV TEST_STEP_ACTION_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV TEST_STEP_ACTION_AGENT_MODEL_PROVIDER="google"
ENV TEST_STEP_ACTION_AGENT_PROMPT_VERSION="v1.1.0"

# Test Step Verification Agent
ENV TEST_STEP_VERIFICATION_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER="google"
ENV TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION="v1.1.0"

# UI Element Description Extraction Agent
ENV UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_PROVIDER="google"
ENV UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_PROMPT_VERSION="v1.0.0"

# UI Element Description Matcher Agent
ENV UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER="google"
ENV UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION="v1.0.0"

# UI State Check Agent
ENV UI_STATE_CHECK_AGENT_MODEL_NAME="gemini-3.5-flash"
ENV UI_STATE_CHECK_AGENT_MODEL_PROVIDER="google"
ENV UI_STATE_CHECK_AGENT_PROMPT_VERSION="v1.0.0"

# API Keys and Endpoints
# GOOGLE_API_KEY passed at runtime from host environment
# GROQ_API_KEY passed at runtime from host environment
# Vector DB (VECTOR_DB_URL and VECTOR_DB_KEY passed at runtime from host environment)
ENV GOOGLE_LOCATION=""
ENV GOOGLE_PROJECT=""
ENV GROQ_ENDPOINT="https://api.groq.com/openai/v1"