#!/bin/bash
#
# ui-test-execution-agent - ${project.description}
# Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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

# Export DISPLAY so all child processes (including Chrome) inherit it
export DISPLAY=:1

# Run Java application as ubuntu user
# Using 'su -p -c' to execute the command in a non-interactive shell while preserving environment variables
su -p ubuntu -c "java -jar ${APP_JAR_PATH}"

echo "Agent application launched."