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

# This script starts websockify with SSL for secure noVNC access.
# It is used in cloud deployments to enable HTTPS access to the VNC session.

# Kill any existing websockify process on the SSL port
echo "Attempting to kill existing websockify process on port $NO_VNC_PORT..."

# Find PIDs using lsof (for processes listening on the port) and pgrep (for websockify)
ALL_PIDS=$( (lsof -t -i:$NO_VNC_PORT 2>/dev/null || true; pgrep -f websockify 2>/dev/null || true) | sort -u )

if [ -n "$ALL_PIDS" ]; then
    echo "Found websockify PIDs: $ALL_PIDS. Killing them..."
    kill -9 $ALL_PIDS 2>/dev/null || true
    echo "Killed websockify processes."
    sleep 2 # Give some time for the port to be released
else
    echo "No websockify process found on port $NO_VNC_PORT or by name."
fi

# Find the correct websockify path
# In Ubuntu 24.04, websockify is installed via python3-websockify package
WEBSOCKIFY_PATH=""
if command -v websockify &> /dev/null; then
    WEBSOCKIFY_PATH="websockify"
elif [ -f "/usr/bin/websockify" ]; then
    WEBSOCKIFY_PATH="/usr/bin/websockify"
elif [ -f "/usr/share/novnc/utils/websockify/run" ]; then
    WEBSOCKIFY_PATH="/usr/share/novnc/utils/websockify/run"
else
    echo "Error: websockify not found!"
    exit 1
fi

# Find the correct noVNC web root path
NOVNC_WEB_ROOT=""
if [ -d "/usr/share/novnc" ]; then
    NOVNC_WEB_ROOT="/usr/share/novnc"
elif [ -d "/usr/share/noVNC" ]; then
    NOVNC_WEB_ROOT="/usr/share/noVNC"
else
    echo "Error: noVNC web root not found!"
    exit 1
fi

# Start websockify with SSL on the specified port
echo "Starting websockify with SSL on port $NO_VNC_PORT..."
echo "Using websockify: $WEBSOCKIFY_PATH"
echo "Using noVNC web root: $NOVNC_WEB_ROOT"

$WEBSOCKIFY_PATH --web $NOVNC_WEB_ROOT --cert /etc/ssl/novnc/novnc.crt --key /etc/ssl/novnc/novnc.key $NO_VNC_PORT localhost:5901 &

echo "websockify with SSL started."