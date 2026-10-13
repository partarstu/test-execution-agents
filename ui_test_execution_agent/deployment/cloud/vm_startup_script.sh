#!/bin/bash
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

set -e

# This script runs on the GCE VM at startup.

# --- Configuration from VM Metadata ---
PROJECT_ID=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcp-project-id" -H "Metadata-Flavor: Google")
SERVICE_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcp-service-name" -H "Metadata-Flavor: Google")
IMAGE_TAG=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcp-image-tag" -H "Metadata-Flavor: Google")
AGENT_SERVER_PORT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/agent-server-port" -H "Metadata-Flavor: Google")
PANEL_PORT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/panel-port" -H "Metadata-Flavor: Google")
APP_FINAL_LOG_FOLDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/app-final-log-folder" -H "Metadata-Flavor: Google")
VNC_RESOLUTION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/VNC_RESOLUTION" -H "Metadata-Flavor: Google")
LOG_LEVEL=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/LOG_LEVEL" -H "Metadata-Flavor: Google")
LOG_FORMAT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/LOG_FORMAT" -H "Metadata-Flavor: Google")
EXECUTION_MODE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/EXECUTION_MODE" -H "Metadata-Flavor: Google")
PANEL_LIVE_CONTROL_ENABLED=$(curl -s -f "http://metadata.google.internal/computeMetadata/v1/instance/attributes/PANEL_LIVE_CONTROL_ENABLED" -H "Metadata-Flavor: Google" || echo "false")
DEBUG_MODE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/DEBUG_MODE" -H "Metadata-Flavor: Google")
SCREEN_RECORDING_ENABLED=$(curl -s -f "http://metadata.google.internal/computeMetadata/v1/instance/attributes/SCREEN_RECORDING_ENABLED" -H "Metadata-Flavor: Google" || echo "false")
JAVA_APP_STARTUP_SCRIPT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/java-app-startup-script" -H "Metadata-Flavor: Google")
AGENT_INTERNAL_IP=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/ip" -H "Metadata-Flavor: Google")
AGENT_EXTERNAL_IP=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip" -H "Metadata-Flavor: Google")

# Screenshot and bounding box settings
BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS" -H "Metadata-Flavor: Google")
BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS" -H "Metadata-Flavor: Google")
BOUNDING_BOX_ALREADY_NORMALIZED=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/BOUNDING_BOX_ALREADY_NORMALIZED" -H "Metadata-Flavor: Google")

# DB Element Selection Agent
DB_ELEMENT_SELECTION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/DB_ELEMENT_SELECTION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# Element Bounding Box Agent
ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# Element Selection Agent
ELEMENT_SELECTION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_SELECTION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
ELEMENT_SELECTION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_SELECTION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
ELEMENT_SELECTION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/ELEMENT_SELECTION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# Knowledge Collection Element Resolution Agent
KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# Knowledge Suggestion Agent
KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

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

# UI Element Description Extraction Agent
UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# UI Element Description Matcher Agent
UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# UI State Check Agent
UI_STATE_CHECK_AGENT_MODEL_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_STATE_CHECK_AGENT_MODEL_NAME" -H "Metadata-Flavor: Google")
UI_STATE_CHECK_AGENT_MODEL_PROVIDER=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_STATE_CHECK_AGENT_MODEL_PROVIDER" -H "Metadata-Flavor: Google")
UI_STATE_CHECK_AGENT_PROMPT_VERSION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/UI_STATE_CHECK_AGENT_PROMPT_VERSION" -H "Metadata-Flavor: Google")

# API Endpoints
GROQ_ENDPOINT_METADATA=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/GROQ_ENDPOINT" -H "Metadata-Flavor: Google")
GOOGLE_LOCATION=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/GOOGLE_LOCATION" -H "Metadata-Flavor: Google")
GOOGLE_PROJECT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/GOOGLE_PROJECT" -H "Metadata-Flavor: Google")

# OpenTelemetry metrics export. Optional: an attribute which was not set leaves the value empty, and without an OTLP
# endpoint the agent exports nothing.
OTEL_METADATA_URL="http://metadata.google.internal/computeMetadata/v1/instance/attributes"
OTEL_SERVICE_NAME=$(curl -s -f "${OTEL_METADATA_URL}/OTEL_SERVICE_NAME" -H "Metadata-Flavor: Google" || echo "")
OTEL_EXPORTER_OTLP_ENDPOINT=$(curl -s -f "${OTEL_METADATA_URL}/OTEL_EXPORTER_OTLP_ENDPOINT" -H "Metadata-Flavor: Google" || echo "")
OTEL_METRICS_EXPORTER=$(curl -s -f "${OTEL_METADATA_URL}/OTEL_METRICS_EXPORTER" -H "Metadata-Flavor: Google" || echo "")
OTEL_METRIC_EXPORT_INTERVAL=$(curl -s -f "${OTEL_METADATA_URL}/OTEL_METRIC_EXPORT_INTERVAL" -H "Metadata-Flavor: Google" || echo "")

# --- Docker Authentication ---
echo "Configuring Docker to authenticate with Google Container Registry..."
mkdir -p /tmp/.docker
export DOCKER_CONFIG=/tmp/.docker
docker-credential-gcr configure-docker

# Registry pulls on a freshly booted VM fail transiently while the network and the credential helper settle, and with
# `set -e` a single failed pull would abort the whole boot.
PULL_ATTEMPTS=10
PULL_RETRY_DELAY_SECONDS=15
retry() {
    local attempt
    for ((attempt = 1; attempt <= PULL_ATTEMPTS; attempt++)); do
        if "$@"; then
            return 0
        fi
        echo "Attempt ${attempt}/${PULL_ATTEMPTS} of '$*' failed, retrying in ${PULL_RETRY_DELAY_SECONDS}s..."
        sleep "${PULL_RETRY_DELAY_SECONDS}"
    done
    echo "'$*' failed after ${PULL_ATTEMPTS} attempts."
    return 1
}

# --- Install Google Cloud SDK (using containerized gcloud) ---
# The image is only used to read secrets, so the tiny :alpine variant (core gcloud) is enough. The image from Google's own
# registry avoids Docker Hub, which rate-limits anonymous pulls.
CLOUD_SDK_IMAGE="gcr.io/google.com/cloudsdktool/google-cloud-cli:alpine"
echo "Pulling ${CLOUD_SDK_IMAGE} image..."
retry docker pull "${CLOUD_SDK_IMAGE}"

# --- Fetch Secrets ---
echo "Fetching secrets from Secret Manager..."
GROQ_API_KEY=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="GROQ_API_KEY" --project="${PROJECT_ID}")
GROQ_ENDPOINT=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="GROQ_ENDPOINT" --project="${PROJECT_ID}")
VECTOR_DB_URL=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="VECTOR_DB_URL" --project="${PROJECT_ID}")
VECTOR_DB_KEY=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="VECTOR_DB_KEY" --project="${PROJECT_ID}")
ANTHROPIC_API_KEY=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="ANTHROPIC_API_KEY" --project="${PROJECT_ID}")
ANTHROPIC_ENDPOINT=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="ANTHROPIC_ENDPOINT" --project="${PROJECT_ID}")
GOOGLE_API_KEY=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="GOOGLE_API_KEY" --project="${PROJECT_ID}")
# Optional: only needed while an agent is switched to the hcompany or the qwen provider.
HCOMPANY_ENDPOINT=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="HCOMPANY_ENDPOINT" --project="${PROJECT_ID}" 2>/dev/null || echo "")
QWEN_ENDPOINT=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="QWEN_ENDPOINT" --project="${PROJECT_ID}" 2>/dev/null || echo "")
NEO4J_USERNAME=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="NEO4J_USERNAME" --project="${PROJECT_ID}" 2>/dev/null || echo "")
# Optional: when set, the agent requires this shared secret as a Bearer token on the main endpoint. Empty disables auth.
AGENT_AUTH_TOKEN=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="AGENT_AUTH_TOKEN" --project="${PROJECT_ID}" 2>/dev/null || echo "")
# Required: the operator panel login and its TLS pair (PEM certificate chain and private key, as contents).
PANEL_PASSWORD=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="PANEL_PASSWORD" --project="${PROJECT_ID}")
PANEL_TLS_CERT=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="PANEL_TLS_CERT" --project="${PROJECT_ID}")
PANEL_TLS_KEY=$(docker run --rm "${CLOUD_SDK_IMAGE}" gcloud secrets versions access latest --secret="PANEL_TLS_KEY" --project="${PROJECT_ID}")
for REQUIRED_SECRET in PANEL_PASSWORD PANEL_TLS_CERT PANEL_TLS_KEY; do
    if [ -z "${!REQUIRED_SECRET}" ]; then
        echo "ERROR: The secret ${REQUIRED_SECRET} is missing or empty in Secret Manager." >&2
        exit 1
    fi
done
# Secrets reach the container by name (-e NAME without a value), so no secret appears in the docker run arguments.
export GROQ_API_KEY GROQ_ENDPOINT VECTOR_DB_URL VECTOR_DB_KEY ANTHROPIC_API_KEY ANTHROPIC_ENDPOINT GOOGLE_API_KEY HCOMPANY_ENDPOINT \
    QWEN_ENDPOINT NEO4J_USERNAME AGENT_AUTH_TOKEN PANEL_PASSWORD PANEL_TLS_CERT PANEL_TLS_KEY

# --- Creating Log Directory on Host ---
echo "Creating log directory on the host..."
mkdir -p /var/log/ui-test-execution-agent
chmod 777 /var/log/ui-test-execution-agent

# --- Run Docker Container ---
echo "Removing any existing service containers"
docker rm -f ${SERVICE_NAME} >/dev/null 2>&1 || true

AGENT_IMAGE="gcr.io/${PROJECT_ID}/${SERVICE_NAME}:${IMAGE_TAG}"
echo "Pulling ${AGENT_IMAGE}..."
retry docker pull "${AGENT_IMAGE}"

echo "Running the Docker container..."
docker run -d --name ${SERVICE_NAME} --shm-size=4g --log-driver=gcplogs \
    -p ${AGENT_SERVER_PORT}:${AGENT_SERVER_PORT} \
    -p ${PANEL_PORT}:${PANEL_PORT} \
    -v /var/log/ui-test-execution-agent:/app/log \
    -e GROQ_API_KEY \
    -e PORT="${AGENT_SERVER_PORT}" \
    -e AGENT_HOST="0.0.0.0" \
    -e EXTERNAL_URL="http://${AGENT_INTERNAL_IP}:${AGENT_SERVER_PORT}" \
    -e GROQ_ENDPOINT \
    -e VECTOR_DB_URL \
    -e VECTOR_DB_KEY \
    -e PANEL_PASSWORD \
    -e PANEL_TLS_CERT \
    -e PANEL_TLS_KEY \
    -e PANEL_PORT="${PANEL_PORT}" \
    -e PANEL_HOST="0.0.0.0" \
    -e PANEL_EXTERNAL_URL="https://${AGENT_EXTERNAL_IP}:${PANEL_PORT}" \
    -e PANEL_LIVE_CONTROL_ENABLED="${PANEL_LIVE_CONTROL_ENABLED}" \
    -e VNC_SOCKET_PATH="/tmp/xvnc.sock" \
    -e VNC_RESOLUTION="${VNC_RESOLUTION}" \
    -e LOG_LEVEL="${LOG_LEVEL}" \
    -e LOG_FORMAT="${LOG_FORMAT}" \
    -e EXECUTION_MODE="${EXECUTION_MODE}" \
    -e DEBUG_MODE="${DEBUG_MODE}" \
    -e ANTHROPIC_API_KEY \
    -e ANTHROPIC_ENDPOINT \
    -e GOOGLE_API_KEY \
    -e HCOMPANY_ENDPOINT \
    -e QWEN_ENDPOINT \
    -e AGENT_AUTH_TOKEN \
    -e logging.level.dev.langchain4j="INFO" \
    -e SCREEN_RECORDING_ENABLED="${SCREEN_RECORDING_ENABLED}" \
    -e SCREEN_RECORDING_FOLDER="/app/log/videos" \
    -e BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS="${BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS}" \
    -e BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS="${BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS}" \
    -e BOUNDING_BOX_ALREADY_NORMALIZED="${BOUNDING_BOX_ALREADY_NORMALIZED}" \
    -e DB_ELEMENT_SELECTION_AGENT_MODEL_NAME="${DB_ELEMENT_SELECTION_AGENT_MODEL_NAME}" \
    -e DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER="${DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER}" \
    -e ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION="${ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION}" \
    -e ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME="${ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME}" \
    -e ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER="${ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER}" \
    -e ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION="${ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION}" \
    -e ELEMENT_SELECTION_AGENT_MODEL_NAME="${ELEMENT_SELECTION_AGENT_MODEL_NAME}" \
    -e ELEMENT_SELECTION_AGENT_MODEL_PROVIDER="${ELEMENT_SELECTION_AGENT_MODEL_PROVIDER}" \
    -e ELEMENT_SELECTION_AGENT_PROMPT_VERSION="${ELEMENT_SELECTION_AGENT_PROMPT_VERSION}" \
    -e KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME="${KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME}" \
    -e KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER="${KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER}" \
    -e KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION="${KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION}" \
    -e KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME="${KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME}" \
    -e KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER="${KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER}" \
    -e KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION="${KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION}" \
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
    -e UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_NAME="${UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_NAME}" \
    -e UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_PROVIDER="${UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_PROVIDER}" \
    -e UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_PROMPT_VERSION="${UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_PROMPT_VERSION}" \
    -e UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME="${UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME}" \
    -e UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER="${UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER}" \
    -e UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION="${UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION}" \
    -e UI_STATE_CHECK_AGENT_MODEL_NAME="${UI_STATE_CHECK_AGENT_MODEL_NAME}" \
    -e UI_STATE_CHECK_AGENT_MODEL_PROVIDER="${UI_STATE_CHECK_AGENT_MODEL_PROVIDER}" \
    -e UI_STATE_CHECK_AGENT_PROMPT_VERSION="${UI_STATE_CHECK_AGENT_PROMPT_VERSION}" \
    -e GOOGLE_LOCATION="${GOOGLE_LOCATION}" \
    -e GOOGLE_PROJECT="${GOOGLE_PROJECT}" \
    -e NEO4J_USERNAME \
    -e OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME}" \
    -e OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT}" \
    -e OTEL_METRICS_EXPORTER="${OTEL_METRICS_EXPORTER}" \
    -e OTEL_METRIC_EXPORT_INTERVAL="${OTEL_METRIC_EXPORT_INTERVAL}" \
    "${AGENT_IMAGE}" ${JAVA_APP_STARTUP_SCRIPT}

echo "Container '${SERVICE_NAME}' is starting."

# Reclaim boot-disk space now that the agent container is running and its image is referenced (so it is kept): drop the
# spent cloud-sdk image used for secrets and any superseded versions. Pruning only after the container starts is what
# keeps the agent image; doing it earlier would delete the cached image and force a needless re-pull.
echo "Pruning unused Docker images to reclaim boot-disk space..."
docker image prune -af || true