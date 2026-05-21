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

# This script provisions a dedicated GCE VM for Neo4j (knowledge persistence).

# --- Configuration ---
export PROJECT_ID=$(gcloud config get-value project)

if [ -z "$PROJECT_ID" ]; then
  echo "Error: No active GCP project is configured."
  echo "Please use 'gcloud config set project <project-id>' to set a project."
  exit 1
fi

echo "Using GCP Project ID: $PROJECT_ID"

# --- Prerequisites ---
echo "Step 1: Checking GCP services..."
# Check if services are already enabled before attempting to enable them
# This avoids permission errors when services are already active
for service in ${GCP_SERVICES}; do
  if gcloud services list --enabled --project=${PROJECT_ID} --filter="name:${service}" --format="value(name)" | grep -q "${service}"; then
    echo "Service ${service} is already enabled."
  else
    echo "Enabling service ${service}..."
    gcloud services enable ${service} --project=${PROJECT_ID}
  fi
done

# --- Persistent Data Disk ---
echo "Step 2: Setting up persistent data disk..."
if ! gcloud compute disks describe ${DATA_DISK_NAME} --project=${PROJECT_ID} --zone=${ZONE} &>/dev/null; then
    echo "Creating persistent disk '${DATA_DISK_NAME}'..."
    gcloud compute disks create ${DATA_DISK_NAME} \
        --project=${PROJECT_ID} \
        --zone=${ZONE} \
        --size=${DATA_DISK_SIZE} \
        --type=${DATA_DISK_TYPE}
else
    echo "Persistent disk '${DATA_DISK_NAME}' already exists."
fi

# --- Networking ---
echo "Step 3: Setting up static IP and firewall rules..."

if ! gcloud compute addresses describe ${STATIC_IP_ADDRESS_NAME} --project=${PROJECT_ID} --region=${REGION} &>/dev/null; then
    echo "Creating static IP address '${STATIC_IP_ADDRESS_NAME}' with STANDARD network tier..."
    gcloud compute addresses create ${STATIC_IP_ADDRESS_NAME} --project=${PROJECT_ID} --region=${REGION} --network-tier=STANDARD
else
    echo "Static IP address '${STATIC_IP_ADDRESS_NAME}' already exists."
fi

if ! gcloud compute firewall-rules describe allow-neo4j --project=${PROJECT_ID} &>/dev/null; then
    echo "Creating firewall rule 'allow-neo4j' for Bolt (${NEO4J_BOLT_PORT:-7687}) and HTTP (7474)..."
    gcloud compute firewall-rules create allow-neo4j \
        --network=${NETWORK_NAME} \
        --allow=tcp:${NEO4J_BOLT_PORT:-7687},tcp:7474 \
        --source-ranges=0.0.0.0/0 \
        --project=${PROJECT_ID}
else
    echo "Firewall rule 'allow-neo4j' already exists."
fi

# --- Neo4j Authentication Secrets ---
echo "Step 4: Setting up Neo4j authentication secrets..."

# NEO4J_USERNAME secret
if ! gcloud secrets describe NEO4J_USERNAME --project=${PROJECT_ID} &>/dev/null; then
    echo "NEO4J_USERNAME secret not found. Creating with default value 'neo4j'..."
    echo -n "neo4j" | gcloud secrets create NEO4J_USERNAME \
        --project=${PROJECT_ID} \
        --replication-policy=automatic \
        --data-file=-
    echo "NEO4J_USERNAME secret created successfully."
else
    echo "NEO4J_USERNAME secret already exists."
fi

if ! gcloud secrets versions access latest --secret="NEO4J_USERNAME" --project=${PROJECT_ID} &>/dev/null; then
    echo "Error: NEO4J_USERNAME secret exists but has no accessible version." >&2
    exit 1
fi
echo "NEO4J_USERNAME secret verified."

# VECTOR_DB_KEY secret
if ! gcloud secrets describe VECTOR_DB_KEY --project=${PROJECT_ID} &>/dev/null; then
    echo "VECTOR_DB_KEY secret not found. Creating with auto-generated password..."
    NEO4J_GENERATED_PASSWORD=$(head -c 32 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 24)
    echo -n "${NEO4J_GENERATED_PASSWORD}" | gcloud secrets create VECTOR_DB_KEY \
        --project=${PROJECT_ID} \
        --replication-policy=automatic \
        --data-file=-
    echo "VECTOR_DB_KEY secret created successfully."
    echo "IMPORTANT: Save this password if you need it for local development."
    echo "  Retrieve it with: gcloud secrets versions access latest --secret=VECTOR_DB_KEY --project=${PROJECT_ID}"
else
    echo "VECTOR_DB_KEY secret already exists."
fi

if ! gcloud secrets versions access latest --secret="VECTOR_DB_KEY" --project=${PROJECT_ID} &>/dev/null; then
    echo "Error: VECTOR_DB_KEY secret exists but has no accessible version." >&2
    exit 1
fi
echo "VECTOR_DB_KEY secret verified."

# --- Secret Manager Access ---
echo "Step 5: Granting Secret Manager access..."
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:$(gcloud projects describe ${PROJECT_ID} --format='value(projectNumber)')-compute@developer.gserviceaccount.com" \
    --role="${SECRET_ACCESSOR_ROLE}" --condition=None

# --- Deploy GCE VM ---
echo "Step 6: Creating GCE instance for Neo4j..."

# Delete the instance if it exists
if gcloud compute instances describe ${INSTANCE_NAME} --project=${PROJECT_ID} --zone=${ZONE} &>/dev/null; then
    echo "Instance '${INSTANCE_NAME}' found. Deleting it..."
    gcloud compute instances delete ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --quiet
    echo "Instance '${INSTANCE_NAME}' deleted."
fi

# Prepare metadata
METADATA="enable-osconfig=TRUE"
METADATA+=",gcp-project-id=${PROJECT_ID}"
METADATA+=",NEO4J_HEAP_SIZE=${NEO4J_HEAP_SIZE}"
METADATA+=",NEO4J_PAGECACHE_SIZE=${NEO4J_PAGECACHE_SIZE}"
METADATA+=",NEO4J_BOLT_PORT=${NEO4J_BOLT_PORT:-7687}"

# Create new instance with attached persistent data disk
gcloud beta compute instances create ${INSTANCE_NAME} \
    --project=${PROJECT_ID} \
    --zone=${ZONE} \
    --machine-type=${MACHINE_TYPE} \
    --network-interface=network-tier=STANDARD,subnet=${SUBNET_NAME},address=${STATIC_IP_ADDRESS_NAME} \
    --provisioning-model=${PROVISIONING_MODEL} \
    --instance-termination-action=${INSTANCE_TERMINATION_ACTION} \
    --service-account=$(gcloud projects describe ${PROJECT_ID} --format='value(projectNumber)')-compute@developer.gserviceaccount.com \
    --scopes=${CLOUD_PLATFORM_SCOPE} \
    --image=${GCE_IMAGE} \
    --boot-disk-size=${BOOT_DISK_SIZE} \
    --boot-disk-type=${BOOT_DISK_TYPE} \
    --boot-disk-device-name=${INSTANCE_NAME} \
    --disk=name=${DATA_DISK_NAME},device-name=${DATA_DISK_NAME},mode=rw,boot=no \
    --max-run-duration=${MAX_VM_RUN_DURATION} \
    --metadata-from-file=startup-script=ui_test_execution_agent/deployment/neo4j/neo4j_vm_startup.sh \
    --metadata=${METADATA} \
    --labels=container-vm=${CONTAINER_VM_LABEL}

echo "Waiting for instance ${INSTANCE_NAME} to be running..."
while [[ $(gcloud compute instances describe ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --format='value(status)') != "RUNNING" ]]; do
  echo -n "."
  sleep ${INSTANCE_STATUS_CHECK_INTERVAL}
done
echo "Instance is running."

# --- Output Connection Info ---
echo "Fetching instance details..."
EXTERNAL_IP=$(gcloud compute instances describe ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --format='value(networkInterfaces[0].accessConfigs[0].natIP)')
INTERNAL_IP=$(gcloud compute instances describe ${INSTANCE_NAME} --zone=${ZONE} --project=${PROJECT_ID} --format='value(networkInterfaces[0].networkIP)')

echo "--- Neo4j Deployment Summary ---"
echo "Neo4j VM '${INSTANCE_NAME}' created."
echo "Internal IP (for VPC connections): ${INTERNAL_IP}"
echo "External IP (for local development): ${EXTERNAL_IP}"
echo "Bolt URI (internal): bolt://${INTERNAL_IP}:${NEO4J_BOLT_PORT:-7687}"
echo "Bolt URI (external): bolt://${EXTERNAL_IP}:${NEO4J_BOLT_PORT:-7687}"
echo "HTTP Browser (external): http://${EXTERNAL_IP}:7474"
echo "Authentication: enabled (credentials from NEO4J_USERNAME and VECTOR_DB_KEY secrets)"
echo ""
echo "Use the internal IP as _NEO4J_HOST when deploying the UI agent:"
echo "  _NEO4J_HOST=${INTERNAL_IP}"
echo ""
echo "To retrieve credentials for local development:"
echo "  gcloud secrets versions access latest --secret=NEO4J_USERNAME --project=${PROJECT_ID}"
echo "  gcloud secrets versions access latest --secret=VECTOR_DB_KEY --project=${PROJECT_ID}"
