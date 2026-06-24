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

# This script runs on the Neo4j GCE VM at startup.

# --- Configuration from VM Metadata ---
PROJECT_ID=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/gcp-project-id" -H "Metadata-Flavor: Google")
NEO4J_HEAP_SIZE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/NEO4J_HEAP_SIZE" -H "Metadata-Flavor: Google")
NEO4J_PAGECACHE_SIZE=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/NEO4J_PAGECACHE_SIZE" -H "Metadata-Flavor: Google")
NEO4J_BOLT_PORT=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/NEO4J_BOLT_PORT" -H "Metadata-Flavor: Google")
NEO4J_BOLT_PORT="${NEO4J_BOLT_PORT:-7687}"
DATA_DISK_NAME=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/DATA_DISK_NAME" -H "Metadata-Flavor: Google")
DATA_DISK_NAME="${DATA_DISK_NAME:-neo4j-data-disk}"

# --- Install Google Cloud SDK (using containerized gcloud) ---
# The image is only used to read secrets, so the tiny :alpine variant (core gcloud) is enough.
echo "Pulling google/cloud-sdk image..."
docker pull google/cloud-sdk:alpine

# --- Fetch Authentication Credentials ---
echo "Fetching Neo4j credentials from Secret Manager..."
NEO4J_USERNAME=$(docker run --rm google/cloud-sdk:alpine gcloud secrets versions access latest --secret="NEO4J_USERNAME" --project="${PROJECT_ID}" 2>/dev/null)
VECTOR_DB_KEY=$(docker run --rm google/cloud-sdk:alpine gcloud secrets versions access latest --secret="VECTOR_DB_KEY" --project="${PROJECT_ID}" 2>/dev/null)

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

# The VM reboots every few hours (maxRunDuration STOP) and the boot disk is small. Now that the credentials are read,
# prune every image no longer referenced by a container: this drops the cloud-sdk image used above and any superseded
# versions while keeping the running Neo4j image, so later pulls don't fill the boot disk with "no space left on device".
echo "Pruning unused Docker images to reclaim boot-disk space..."
docker image prune -af || true

# --- Mount Persistent Data Disk ---
echo "Setting up persistent data disk..."
DATA_DISK_DEVICE="/dev/disk/by-id/google-${DATA_DISK_NAME}"
DATA_MOUNT_POINT="/var/lib/neo4j-data"

# Create mount point directory - /var/lib is writable on COS
mkdir -p "${DATA_MOUNT_POINT}"

# The data disk holds the entire database. If it isn't attached, mounting silently falls back to the empty
# boot-disk directory and Neo4j starts with a blank store. Fail loudly instead of destroying the illusion of data.
if [ ! -b "${DATA_DISK_DEVICE}" ]; then
    echo "ERROR: Data disk ${DATA_DISK_DEVICE} is not attached. Aborting to avoid starting with an empty database." >&2
    exit 1
fi

# Format only a genuinely empty disk (first boot of a brand-new disk). An existing filesystem is never reformatted.
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
