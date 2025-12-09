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

# This script is executed after the main VNC and desktop services are running.

# --- Wait for X server to be ready (with timeout) ---
# This loop waits until the X11 socket for display :1 exists.
MAX_RETRIES=60 # 60 retries * 0.5s sleep = 30 seconds timeout
RETRY_COUNT=0

echo "Waiting for X server on display :1 to be ready..."
while [ ! -e /tmp/.X11-unix/X1 ]; do
  if [ ${RETRY_COUNT} -ge ${MAX_RETRIES} ]; then
    echo "ERROR: Timed out after ${MAX_RETRIES} retries. X server did not start." >&2
    exit 1
  fi
  RETRY_COUNT=$((RETRY_COUNT + 1))
  sleep 0.5
done
echo "X server is ready."

if [ "$DEPLOYMENT_ENV" = "cloud" ]; then
  echo "Cloud deployment detected. Starting websockify with SSL in order to serve noVNC on HTTPS"
  /app/start_websockify_ssl.sh
else
  echo "Local deployment detected. Skipping websockify SSL startup."
fi

echo "Launching Java application from ${APP_JAR_PATH}"
# Check if the APP_JAR_PATH is set and the file exists
if [ -z "${APP_JAR_PATH}" ] || [ ! -f "${APP_JAR_PATH}" ]; then
  echo "ERROR: APP_JAR_PATH environment variable is not set or the file does not exist at '${APP_JAR_PATH}'." >&2
  exit 1
fi

su headless
DISPLAY=:1 java -jar ${APP_JAR_PATH}

echo "Agent application launched."