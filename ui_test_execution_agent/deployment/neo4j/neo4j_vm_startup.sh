#!/bin/bash
#
# Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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

# This script runs on the Neo4j GCE VM at startup.

# --- Configuration from VM Metadata ---
PROJECT_ID=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcp-project-id" -H "Metadata-Flavor: Google")
NEO4J_HEAP_SIZE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/NEO4J_HEAP_SIZE" -H "Metadata-Flavor: Google")
NEO4J_PAGECACHE_SIZE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/NEO4J_PAGECACHE_SIZE" -H "Metadata-Flavor: Google")
NEO4J_BOLT_PORT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/NEO4J_BOLT_PORT" -H "Metadata-Flavor: Google")
NEO4J_BOLT_PORT="${NEO4J_BOLT_PORT:-7687}"

# --- Install Google Cloud SDK (using containerized gcloud) ---
echo "Pulling google/cloud-sdk image..."
docker pull google/cloud-sdk:latest

# --- Fetch Authentication Credentials ---
echo "Fetching Neo4j credentials from Secret Manager..."
NEO4J_USERNAME=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="NEO4J_USERNAME" --project="${PROJECT_ID}" 2>/dev/null)
VECTOR_DB_KEY=$(docker run --rm google/cloud-sdk:latest gcloud secrets versions access latest --secret="VECTOR_DB_KEY" --project="${PROJECT_ID}" 2>/dev/null)

if [ -z "${NEO4J_USERNAME}" ]; then
    echo "ERROR: NEO4J_USERNAME secret not found or empty in project '${PROJECT_ID}'." >&2
    echo "Run deploy_neo4j_vm.sh which auto-creates it, or create manually:" >&2
    echo "  echo -n 'neo4j' | gcloud secrets create NEO4J_USERNAME --data-file=- --project=${PROJECT_ID}" >&2
    exit 1
fi

if [ -z "${VECTOR_DB_KEY}" ]; then
    echo "ERROR: VECTOR_DB_KEY secret not found or empty in project '${PROJECT_ID}'." >&2
    echo "Run deploy_neo4j_vm.sh which auto-creates it, or create manually:" >&2
    echo "  gcloud secrets create VECTOR_DB_KEY --data-file=<password-file> --project=${PROJECT_ID}" >&2
    exit 1
fi
echo "Neo4j authentication credentials retrieved successfully (user=${NEO4J_USERNAME})."

# --- Mount Persistent Data Disk ---
echo "Setting up persistent data disk..."
DATA_DISK_DEVICE="/dev/disk/by-id/google-neo4j-data-disk"
DATA_MOUNT_POINT="/var/lib/neo4j-data"

# Create mount point directory - /var/lib is writable on COS
mkdir -p "${DATA_MOUNT_POINT}"

# Format the disk only if it has no filesystem (first boot)
if ! blkid "${DATA_DISK_DEVICE}" &>/dev/null; then
    echo "Formatting data disk (first boot)..."
    mkfs.ext4 -m 0 -F -E lazy_itable_init=0,lazy_journal_init=0,discard "${DATA_DISK_DEVICE}"
fi

mount -o discard,defaults "${DATA_DISK_DEVICE}" "${DATA_MOUNT_POINT}"
chmod 777 "${DATA_MOUNT_POINT}"
echo "Data disk mounted at ${DATA_MOUNT_POINT}"

# --- Start Neo4j Container ---
echo "Starting Neo4j container with authentication enabled..."
NEO4J_CONTAINER_NAME="neo4j-knowledge"

docker rm -f "${NEO4J_CONTAINER_NAME}" >/dev/null 2>&1 || true
docker run -d \
    --name "${NEO4J_CONTAINER_NAME}" \
    --restart unless-stopped \
    --log-driver=gcplogs \
    -p ${NEO4J_BOLT_PORT}:${NEO4J_BOLT_PORT} \
    -p 7474:7474 \
    -v "${DATA_MOUNT_POINT}:/data" \
    -e NEO4J_AUTH="${NEO4J_USERNAME}/${VECTOR_DB_KEY}" \
    -e NEO4J_dbms_security_auth__enabled="true" \
    -e NEO4J_server_memory_heap_initial__size="${NEO4J_HEAP_SIZE}" \
    -e NEO4J_server_memory_heap_max__size="${NEO4J_HEAP_SIZE}" \
    -e NEO4J_server_memory_pagecache_size="${NEO4J_PAGECACHE_SIZE}" \
    -e NEO4J_server_default__listen__address="0.0.0.0" \
    neo4j:2026.01.3-community

# --- Wait for Neo4j Readiness ---
echo "Waiting for Neo4j to become ready..."
MAX_RETRIES=60
RETRY_COUNT=0
while ! docker exec "${NEO4J_CONTAINER_NAME}" neo4j status 2>/dev/null | grep -q "running"; do
    if [ "${RETRY_COUNT}" -ge "${MAX_RETRIES}" ]; then
        echo "ERROR: Neo4j did not start within ${MAX_RETRIES} seconds." >&2
        docker logs "${NEO4J_CONTAINER_NAME}" >&2
        exit 1
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    sleep 1
done

# --- Verify Authentication ---
echo "Verifying Neo4j authentication..."
AUTH_CHECK=$(docker exec "${NEO4J_CONTAINER_NAME}" cypher-shell -u "${NEO4J_USERNAME}" -p "${VECTOR_DB_KEY}" "RETURN 1 AS result" 2>&1) || true
if echo "${AUTH_CHECK}" | grep -q "result"; then
    echo "Neo4j authentication verified successfully."
else
    echo "WARNING: Neo4j authentication verification failed. Output: ${AUTH_CHECK}" >&2
    echo "The container is running but authentication may need manual attention." >&2
fi

INTERNAL_IP=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/ip" -H "Metadata-Flavor: Google")
echo "Neo4j is running on bolt://${INTERNAL_IP}:${NEO4J_BOLT_PORT} with authentication enabled (user=${NEO4J_USERNAME})"
