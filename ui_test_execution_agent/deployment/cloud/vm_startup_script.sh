#!/bin/bash
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

set -e

# This script runs on the GCE VM at startup.

# --- Configuration from VM Metadata ---
PROJECT_ID=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcp-project-id" -H "Metadata-Flavor: Google")
SERVICE_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcp-service-name" -H "Metadata-Flavor: Google")
IMAGE_TAG=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcp-image-tag" -H "Metadata-Flavor: Google")
NO_VNC_PORT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/no-vnc-port" -H "Metadata-Flavor: Google")
VNC_PORT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/vnc-port" -H "Metadata-Flavor: Google")
AGENT_SERVER_PORT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/agent-server-port" -H "Metadata-Flavor: Google")
APP_FINAL_LOG_FOLDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/app-final-log-folder" -H "Metadata-Flavor: Google")
VNC_RESOLUTION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/VNC_RESOLUTION" -H "Metadata-Flavor: Google")
LOG_LEVEL=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/LOG_LEVEL" -H "Metadata-Flavor: Google")
UNATTENDED_MODE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UNATTENDED_MODE" -H "Metadata-Flavor: Google")
DEBUG_MODE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/DEBUG_MODE" -H "Metadata-Flavor: Google")
JAVA_APP_STARTUP_SCRIPT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/java-app-startup-script" -H "Metadata-Flavor: Google")
AGENT_INTERNAL_IP=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/ip" -H "Metadata-Flavor: Google")

# Screenshot and bounding box settings
BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS" -H "Metadata-Flavor: Google")
BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS" -H "Metadata-Flavor: Google")
BOUNDING_BOX_ALREADY_NORMALIZED=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/BOUNDING_BOX_ALREADY_NORMALIZED" -H "Metadata-Flavor: Google")

# Element Bounding Box Agent
ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# Element Selection Agent
ELEMENT_SELECTION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_SELECTION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
ELEMENT_SELECTION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_SELECTION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
ELEMENT_SELECTION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_SELECTION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# Page Description Agent
PAGE_DESCRIPTION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/PAGE_DESCRIPTION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
PAGE_DESCRIPTION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/PAGE_DESCRIPTION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
PAGE_DESCRIPTION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/PAGE_DESCRIPTION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# Precondition Agent
PRECONDITION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/PRECONDITION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
PRECONDITION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/PRECONDITION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
PRECONDITION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/PRECONDITION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# Precondition Verification Agent
PRECONDITION_VERIFICATION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/PRECONDITION_VERIFICATION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# Test Case Extraction Agent
TEST_CASE_EXTRACTION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/TEST_CASE_EXTRACTION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# Test Step Action Agent
TEST_STEP_ACTION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/TEST_STEP_ACTION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
TEST_STEP_ACTION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/TEST_STEP_ACTION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
TEST_STEP_ACTION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/TEST_STEP_ACTION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# Test Step Verification Agent
TEST_STEP_VERIFICATION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/TEST_STEP_VERIFICATION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# UI Element Description Agent
UI_ELEMENT_DESCRIPTION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_ELEMENT_DESCRIPTION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
UI_ELEMENT_DESCRIPTION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_ELEMENT_DESCRIPTION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
UI_ELEMENT_DESCRIPTION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_ELEMENT_DESCRIPTION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# UI State Check Agent
UI_STATE_CHECK_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_STATE_CHECK_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
UI_STATE_CHECK_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_STATE_CHECK_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
UI_STATE_CHECK_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_STATE_CHECK_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# API Endpoints
GROQ_ENDPOINT_METADATA=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/GROQ_ENDPOINT" -H "Metadata-Flavor: Google")
GOOGLE_LOCATION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/GOOGLE_LOCATION" -H "Metadata-Flavor: Google")
GOOGLE_PROJECT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/GOOGLE_PROJECT" -H "Metadata-Flavor: Google")

# --- Docker Authentication ---
echo "Configuring Docker to authenticate with Google Container Registry..."
mkdir -p /tmp/.docker
export DOCKER_CONFIG=/tmp/.docker
docker-credential-gcr configure-docker

# --- Install Google Cloud SDK (using containerized gcloud) ---
echo "Pulling google/cloud-sdk image..."
docker pull google/cloud-sdk:latest

# --- Fetch Secrets ---
echo "Fetching secrets from Secret Manager..."
GROQ_API_KEY=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="GROQ_API_KEY" --project="${PROJECT_ID}")
GROQ_ENDPOINT=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="GROQ_ENDPOINT" --project="${PROJECT_ID}")
VECTOR_DB_URL=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="VECTOR_DB_URL" --project="${PROJECT_ID}")
VECTOR_DB_KEY=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="VECTOR_DB_KEY" --project="${PROJECT_ID}")
VNC_PW=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="VNC_PW" --project="${PROJECT_ID}")
ANTHROPIC_API_KEY=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="ANTHROPIC_API_KEY" --project="${PROJECT_ID}")
ANTHROPIC_ENDPOINT=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="ANTHROPIC_ENDPOINT" --project="${PROJECT_ID}")
GOOGLE_API_KEY=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="GOOGLE_API_KEY" --project="${PROJECT_ID}")

# --- Creating Log Directory on Host ---
echo "Creating log directory on the host..."
mkdir -p /var/log/ui-test-execution-agent
chmod 777 /var/log/ui-test-execution-agent

# --- Run Docker Container ---
echo "Removing any existing service containers"
docker rm -f ${SERVICE_NAME} >/dev/null 2>&1 || true

echo "Pulling and running the Docker container..."
docker run -d --name ${SERVICE_NAME} --shm-size=4g --log-driver=gcplogs \
    -p ${NO_VNC_PORT}:${NO_VNC_PORT} \
    -p ${VNC_PORT}:${VNC_PORT} \
    -p ${AGENT_SERVER_PORT}:${AGENT_SERVER_PORT} \
    -v /var/log/ui-test-execution-agent:/app/log \
    -e NO_VNC_PORT="${NO_VNC_PORT}" \
    -e GROQ_API_KEY="${GROQ_API_KEY}" \
    -e PORT="${AGENT_SERVER_PORT}" \
    -e AGENT_HOST="0.0.0.0" \
    -e EXTERNAL_URL="http://${AGENT_INTERNAL_IP}:${AGENT_SERVER_PORT}" \
    -e GROQ_ENDPOINT="${GROQ_ENDPOINT}" \
    -e VECTOR_DB_URL="${VECTOR_DB_URL}" \
    -e VECTOR_DB_KEY="${VECTOR_DB_KEY}" \
    -e VNC_PW="${VNC_PW}" \
    -e VNC_RESOLUTION="${VNC_RESOLUTION}" \
    -e LOG_LEVEL="${LOG_LEVEL}" \
    -e UNATTENDED_MODE="${UNATTENDED_MODE}" \
    -e DEBUG_MODE="${DEBUG_MODE}" \
    -e ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY}" \
    -e ANTHROPIC_ENDPOINT="${ANTHROPIC_ENDPOINT}" \
    -e GOOGLE_API_KEY="${GOOGLE_API_KEY}" \
    -e WEBSOCKIFY_ENABLED=false \
    -e logging.level.dev.langchain4j="INFO" \
    -e SCREEN_RECORDING_FOLDER="/app/log/videos" \
    -e BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS="${BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS}" \
    -e BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS="${BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS}" \
    -e BOUNDING_BOX_ALREADY_NORMALIZED="${BOUNDING_BOX_ALREADY_NORMALIZED}" \
    -e ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME="${ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME}" \
    -e ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER="${ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER}" \
    -e ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION="${ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION}" \
    -e ELEMENT_SELECTION_AGENT_MODEL_NAME="${ELEMENT_SELECTION_AGENT_MODEL_NAME}" \
    -e ELEMENT_SELECTION_AGENT_MODEL_PROVIDER="${ELEMENT_SELECTION_AGENT_MODEL_PROVIDER}" \
    -e ELEMENT_SELECTION_AGENT_PROMPT_VERSION="${ELEMENT_SELECTION_AGENT_PROMPT_VERSION}" \
    -e PAGE_DESCRIPTION_AGENT_MODEL_NAME="${PAGE_DESCRIPTION_AGENT_MODEL_NAME}" \
    -e PAGE_DESCRIPTION_AGENT_MODEL_PROVIDER="${PAGE_DESCRIPTION_AGENT_MODEL_PROVIDER}" \
    -e PAGE_DESCRIPTION_AGENT_PROMPT_VERSION="${PAGE_DESCRIPTION_AGENT_PROMPT_VERSION}" \
    -e PRECONDITION_AGENT_MODEL_NAME="${PRECONDITION_AGENT_MODEL_NAME}" \
    -e PRECONDITION_AGENT_MODEL_PROVIDER="${PRECONDITION_AGENT_MODEL_PROVIDER}" \
    -e PRECONDITION_AGENT_PROMPT_VERSION="${PRECONDITION_AGENT_PROMPT_VERSION}" \
    -e PRECONDITION_VERIFICATION_AGENT_MODEL_NAME="${PRECONDITION_VERIFICATION_AGENT_MODEL_NAME}" \
    -e PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER="${PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER}" \
    -e PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION="${PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION}" \
    -e TEST_CASE_EXTRACTION_AGENT_MODEL_NAME="${TEST_CASE_EXTRACTION_AGENT_MODEL_NAME}" \
    -e TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER="${TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER}" \
    -e TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION="${TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION}" \
    -e TEST_STEP_ACTION_AGENT_MODEL_NAME="${TEST_STEP_ACTION_AGENT_MODEL_NAME}" \
    -e TEST_STEP_ACTION_AGENT_MODEL_PROVIDER="${TEST_STEP_ACTION_AGENT_MODEL_PROVIDER}" \
    -e TEST_STEP_ACTION_AGENT_PROMPT_VERSION="${TEST_STEP_ACTION_AGENT_PROMPT_VERSION}" \
    -e TEST_STEP_VERIFICATION_AGENT_MODEL_NAME="${TEST_STEP_VERIFICATION_AGENT_MODEL_NAME}" \
    -e TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER="${TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER}" \
    -e TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION="${TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION}" \
    -e UI_ELEMENT_DESCRIPTION_AGENT_MODEL_NAME="${UI_ELEMENT_DESCRIPTION_AGENT_MODEL_NAME}" \
    -e UI_ELEMENT_DESCRIPTION_AGENT_MODEL_PROVIDER="${UI_ELEMENT_DESCRIPTION_AGENT_MODEL_PROVIDER}" \
    -e UI_ELEMENT_DESCRIPTION_AGENT_PROMPT_VERSION="${UI_ELEMENT_DESCRIPTION_AGENT_PROMPT_VERSION}" \
    -e UI_STATE_CHECK_AGENT_MODEL_NAME="${UI_STATE_CHECK_AGENT_MODEL_NAME}" \
    -e UI_STATE_CHECK_AGENT_MODEL_PROVIDER="${UI_STATE_CHECK_AGENT_MODEL_PROVIDER}" \
    -e UI_STATE_CHECK_AGENT_PROMPT_VERSION="${UI_STATE_CHECK_AGENT_PROMPT_VERSION}" \
    -e GOOGLE_LOCATION="${GOOGLE_LOCATION}" \
    -e GOOGLE_PROJECT="${GOOGLE_PROJECT}" \
    gcr.io/${PROJECT_ID}/${SERVICE_NAME}:${IMAGE_TAG} ${JAVA_APP_STARTUP_SCRIPT}

echo "Container '${SERVICE_NAME}' is starting."