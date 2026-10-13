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

# This script provisions a GCE VM and deploys the UI test execution agent.

# --- Configuration ---
# Get the GCP Project ID from the active gcloud configuration.
export PROJECT_ID=$(gcloud config get-value project)

if [ -z "$PROJECT_ID" ]; then
  echo "Error: No active GCP project is configured."
  echo "Please use 'gcloud config set project <project-id>' to set a project."
  exit 1
fi

echo "Using GCP Project ID: $PROJECT_ID"

# The panel can drive the agent's screen and change its knowledge, so opening it to the internet must be a deliberate choice
if [ -z "$FIREWALL_PANEL_SOURCE_RANGES" ]; then
  echo "Error: FIREWALL_PANEL_SOURCE_RANGES (substitution _FIREWALL_PANEL_SOURCE_RANGES) is not set."
  echo "Set it to the comma-separated CIDR ranges allowed to reach the operator panel, e.g. your office or VPN egress range."
  exit 1
fi

# All environment variables are expected to be set by the calling script or cloudbuild.yaml substitutions.
# Only APP_LOG_FINAL_FOLDER is a constant that doesn't need to be configurable.
export APP_LOG_FINAL_FOLDER="/app/log"

# --- Prerequisites ---
echo "Step 1: Enabling necessary GCP services..."
source "$(dirname "$0")/../gcp_services.sh"
enable_missing_gcp_services "${PROJECT_ID}" ${GCP_SERVICES}

# --- Secret Management (IMPORTANT) ---
echo "Step 2: Setting up secrets in Google Secret Manager..."
echo "Please ensure you have created the following secrets in Secret Manager:"
echo " - GROQ_API_KEY"
echo " - GROQ_ENDPOINT"
echo " - VECTOR_DB_URL"
echo " - VECTOR_DB_KEY"
echo " - PANEL_PASSWORD (at least 16 characters, e.g. openssl rand -base64 32)"
echo " - PANEL_TLS_CERT (PEM certificate chain of the operator panel, self-signed or CA-signed)"
echo " - PANEL_TLS_KEY (PEM private key of that certificate)"
echo " - GOOGLE_API_KEY"
echo " - HCOMPANY_ENDPOINT (optional)"
echo " - QWEN_ENDPOINT (optional)"

# --- Networking ---
echo "Step 3: Setting up VPC network and firewall rules..."

if ! gcloud compute addresses describe ${STATIC_IP_ADDRESS_NAME} --project=${PROJECT_ID} --region=${REGION} &>/dev/null; then
    echo "Creating static IP address '${STATIC_IP_ADDRESS_NAME}'..."
    gcloud compute addresses create ${STATIC_IP_ADDRESS_NAME} --project=${PROJECT_ID} --region=${REGION} --network-tier=STANDARD
else
    echo "Static IP address '${STATIC_IP_ADDRESS_NAME}' already exists."
fi

if ! gcloud compute networks describe ${NETWORK_NAME} --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating VPC network '${NETWORK_NAME}'..."
    gcloud compute networks create ${NETWORK_NAME} --subnet-mode=${NETWORK_SUBNET_MODE} --mtu=${NETWORK_MTU} --bgp-routing-mode=${NETWORK_BGP_ROUTING_MODE} --project=${PROJECT_ID}
else
    echo "VPC network '${NETWORK_NAME}' already exists."
fi

# The operator panel replaced noVNC, so its port stays closed
if gcloud compute firewall-rules describe allow-novnc --project=${PROJECT_ID} &>/dev/null; then
    echo "Deleting the obsolete firewall rule 'allow-novnc'..."
    gcloud compute firewall-rules delete allow-novnc --project=${PROJECT_ID} --quiet
fi

if ! gcloud compute firewall-rules describe allow-panel --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-panel' for port ${PANEL_PORT}..."
    gcloud compute firewall-rules create allow-panel --network=${NETWORK_NAME} --allow=tcp:${PANEL_PORT} --source-ranges=${FIREWALL_PANEL_SOURCE_RANGES} --project=${PROJECT_ID}
else
    echo "Updating firewall rule 'allow-panel' to port ${PANEL_PORT} and the configured source ranges..."
    gcloud compute firewall-rules update allow-panel --allow=tcp:${PANEL_PORT} --source-ranges=${FIREWALL_PANEL_SOURCE_RANGES} --project=${PROJECT_ID}
fi

if ! gcloud compute firewall-rules describe allow-app-internal --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-app-internal'..."
    gcloud compute firewall-rules create allow-app-internal --network=${NETWORK_NAME} --allow=tcp:${FIREWALL_APP_INTERNAL_PORTS} --source-ranges=${FIREWALL_APP_INTERNAL_SOURCE_RANGES} --project=${PROJECT_ID}
else
    echo "Firewall rule 'allow-app-internal' already exists."
fi

# The A2A port is reachable only from inside the VPC; an existing rule is updated, because it may still allow 0.0.0.0/0
if ! gcloud compute firewall-rules describe allow-agent-server --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-agent-server' for port ${AGENT_SERVER_PORT}..."
    gcloud compute firewall-rules create allow-agent-server --network=${NETWORK_NAME} --allow=tcp:${AGENT_SERVER_PORT} --source-ranges=${FIREWALL_APP_INTERNAL_SOURCE_RANGES} --project=${PROJECT_ID}
else
    echo "Updating firewall rule 'allow-agent-server' to the internal source ranges..."
    gcloud compute firewall-rules update allow-agent-server --allow=tcp:${AGENT_SERVER_PORT} --source-ranges=${FIREWALL_APP_INTERNAL_SOURCE_RANGES} --project=${PROJECT_ID}
fi

if ! gcloud compute firewall-rules describe allow-ssh --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-ssh'..."
    gcloud compute firewall-rules create allow-ssh --network=${NETWORK_NAME} --allow=tcp:${FIREWALL_SSH_PORT} --source-ranges=${FIREWALL_SSH_SOURCE_RANGES} --project=${PROJECT_ID}
else
    echo "Firewall rule 'allow-ssh' already exists."
fi

# --- Deploy GCE VM ---
echo "Step 4: Creating GCE instance and deploying the container..."

# Delete the instance if it exists
if gcloud compute instances describe ${INSTANCE_NAME} --project=${PROJECT_ID} --zone=${ZONE} &>/dev/null; then
    echo "Instance '${INSTANCE_NAME}' found. Deleting it..."
    gcloud compute instances delete ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --quiet
    echo "Instance '${INSTANCE_NAME}' deleted."
fi

# Manage secrets access
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:$(gcloud projects describe ${PROJECT_ID} --format='value(projectNumber)')-compute@developer.gserviceaccount.com" \
    --role="${SECRET_ACCESSOR_ROLE}" --condition=None

# Prepare metadata (formatted for readability)
METADATA="enable-osconfig=TRUE"
METADATA+=",gcp-project-id=${PROJECT_ID}"
METADATA+=",gcp-service-name=${SERVICE_NAME}"
METADATA+=",gcp-image-tag=${IMAGE_TAG}"
METADATA+=",agent-server-port=${AGENT_SERVER_PORT}"
METADATA+=",panel-port=${PANEL_PORT}"
METADATA+=",app-final-log-folder=${APP_LOG_FINAL_FOLDER}"
METADATA+=",VNC_RESOLUTION=${VNC_RESOLUTION}"
METADATA+=",LOG_LEVEL=${LOG_LEVEL}"
METADATA+=",LOG_FORMAT=${LOG_FORMAT}"
METADATA+=",EXECUTION_MODE=${EXECUTION_MODE}"
METADATA+=",PANEL_LIVE_CONTROL_ENABLED=${PANEL_LIVE_CONTROL_ENABLED}"
METADATA+=",DEBUG_MODE=${DEBUG_MODE}"
METADATA+=",SCREEN_RECORDING_ENABLED=${SCREEN_RECORDING_ENABLED}"
METADATA+=",java-app-startup-script=${JAVA_APP_STARTUP_SCRIPT}"
METADATA+=",BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS=${BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS}"
METADATA+=",BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS=${BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS}"
METADATA+=",BOUNDING_BOX_ALREADY_NORMALIZED=${BOUNDING_BOX_ALREADY_NORMALIZED}"
METADATA+=",DB_ELEMENT_SELECTION_AGENT_MODEL_NAME=${DB_ELEMENT_SELECTION_AGENT_MODEL_NAME}"
METADATA+=",DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER=${DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER}"
METADATA+=",ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION=${ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION}"
METADATA+=",ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME=${ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME}"
METADATA+=",ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER=${ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER}"
METADATA+=",ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION=${ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION}"
METADATA+=",ELEMENT_SELECTION_AGENT_MODEL_NAME=${ELEMENT_SELECTION_AGENT_MODEL_NAME}"
METADATA+=",ELEMENT_SELECTION_AGENT_MODEL_PROVIDER=${ELEMENT_SELECTION_AGENT_MODEL_PROVIDER}"
METADATA+=",ELEMENT_SELECTION_AGENT_PROMPT_VERSION=${ELEMENT_SELECTION_AGENT_PROMPT_VERSION}"
METADATA+=",KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME=${KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME}"
METADATA+=",KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER=${KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER}"
METADATA+=",KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION=${KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION}"
METADATA+=",KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME=${KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME}"
METADATA+=",KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER=${KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER}"
METADATA+=",KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION=${KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION}"
METADATA+=",PRECONDITION_AGENT_MODEL_NAME=${PRECONDITION_AGENT_MODEL_NAME}"
METADATA+=",PRECONDITION_AGENT_MODEL_PROVIDER=${PRECONDITION_AGENT_MODEL_PROVIDER}"
METADATA+=",PRECONDITION_AGENT_PROMPT_VERSION=${PRECONDITION_AGENT_PROMPT_VERSION}"
METADATA+=",PRECONDITION_VERIFICATION_AGENT_MODEL_NAME=${PRECONDITION_VERIFICATION_AGENT_MODEL_NAME}"
METADATA+=",PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER=${PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER}"
METADATA+=",PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION=${PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION}"
METADATA+=",TEST_CASE_EXTRACTION_AGENT_MODEL_NAME=${TEST_CASE_EXTRACTION_AGENT_MODEL_NAME}"
METADATA+=",TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER=${TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER}"
METADATA+=",TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION=${TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION}"
METADATA+=",TEST_STEP_ACTION_AGENT_MODEL_NAME=${TEST_STEP_ACTION_AGENT_MODEL_NAME}"
METADATA+=",TEST_STEP_ACTION_AGENT_MODEL_PROVIDER=${TEST_STEP_ACTION_AGENT_MODEL_PROVIDER}"
METADATA+=",TEST_STEP_ACTION_AGENT_PROMPT_VERSION=${TEST_STEP_ACTION_AGENT_PROMPT_VERSION}"
METADATA+=",TEST_STEP_VERIFICATION_AGENT_MODEL_NAME=${TEST_STEP_VERIFICATION_AGENT_MODEL_NAME}"
METADATA+=",TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER=${TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER}"
METADATA+=",TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION=${TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION}"
METADATA+=",UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_NAME=${UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_NAME}"
METADATA+=",UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_PROVIDER=${UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_PROVIDER}"
METADATA+=",UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_PROMPT_VERSION=${UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_PROMPT_VERSION}"
METADATA+=",UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME=${UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME}"
METADATA+=",UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER=${UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER}"
METADATA+=",UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION=${UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION}"
METADATA+=",UI_STATE_CHECK_AGENT_MODEL_NAME=${UI_STATE_CHECK_AGENT_MODEL_NAME}"
METADATA+=",UI_STATE_CHECK_AGENT_MODEL_PROVIDER=${UI_STATE_CHECK_AGENT_MODEL_PROVIDER}"
METADATA+=",UI_STATE_CHECK_AGENT_PROMPT_VERSION=${UI_STATE_CHECK_AGENT_PROMPT_VERSION}"
METADATA+=",GROQ_ENDPOINT=${GROQ_ENDPOINT}"
METADATA+=",GOOGLE_LOCATION=${GOOGLE_LOCATION}"
METADATA+=",GOOGLE_PROJECT=${GOOGLE_PROJECT}"
# OpenTelemetry metrics export. Empty values keep the export off, which is the default of the agent.
METADATA+=",OTEL_SERVICE_NAME=${OTEL_SERVICE_NAME}"
METADATA+=",OTEL_EXPORTER_OTLP_ENDPOINT=${OTEL_EXPORTER_OTLP_ENDPOINT}"
METADATA+=",OTEL_METRICS_EXPORTER=${OTEL_METRICS_EXPORTER}"
METADATA+=",OTEL_METRIC_EXPORT_INTERVAL=${OTEL_METRIC_EXPORT_INTERVAL}"

# Create new instance
gcloud beta compute instances create ${INSTANCE_NAME} \
    --project=${PROJECT_ID} \
    --zone=${ZONE} \
    --machine-type=${MACHINE_TYPE} \
    --network-interface=network-tier=STANDARD,subnet=${SUBNET_NAME},address=${STATIC_IP_ADDRESS_NAME} \
    --provisioning-model=${PROVISIONING_MODEL} \
    --instance-termination-action=${INSTANCE_TERMINATION_ACTION} \
    --service-account=$(gcloud projects describe ${PROJECT_ID} --format='value(projectNumber)')-compute@developer.gserviceaccount.com \
    --scopes=${CLOUD_PLATFORM_SCOPE} \
    --image-family=${GCE_IMAGE_FAMILY} \
    --image-project=${GCE_IMAGE_PROJECT} \
    --boot-disk-size=${BOOT_DISK_SIZE} \
    --boot-disk-type=${BOOT_DISK_TYPE} \
    --boot-disk-device-name=${INSTANCE_NAME} \
    --labels=goog-ops-agent-policy=v2-x86-template-1-4-0,goog-ec-src=vm_add-gcloud \
    --max-run-duration=${MAX_VM_RUN_DURATION} \
    --metadata-from-file=startup-script=ui_test_execution_agent/deployment/cloud/vm_startup_script.sh \
    --metadata=${METADATA} \
    --labels=container-vm=${CONTAINER_VM_LABEL}

echo "Waiting for instance ${INSTANCE_NAME} to be running..."
while [[ $(gcloud compute instances describe ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --format='value(status)') != "RUNNING" ]]; do
  echo -n "."
  sleep ${INSTANCE_STATUS_CHECK_INTERVAL}
done
echo "Instance is running."

echo "Fetching instance details..."
EXTERNAL_IP=$(gcloud compute instances describe ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --format='value(networkInterfaces[0].accessConfigs[0].natIP)')

echo "--- Deployment Summary ---"
echo "Agent VM '${INSTANCE_NAME}' created."
echo "Agent is running on ${AGENT_SERVER_PORT} port."
echo "In order to get the internal Agent host name, execute the following command inside the VM: 'curl \"http://metadata.google.internal/computeMetadata/v1/instance/hostname\" -H \"Metadata-Flavor: Google\"'"
echo "To supervise the Agent, open the operator panel at https://${EXTERNAL_IP}:${PANEL_PORT}"
echo "It may take a few minutes for the VM to start and agent to be available."