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

# This script starts websockify with SSL for secure noVNC access.
# It is used in cloud deployments to enable HTTPS access to the VNC session.

# Kill any existing websockify process on the SSL port
echo "Attempting to kill existing websockify process on port $NO_VNC_PORT..."

# Get current process ID and parent to exclude them from killing
CURRENT_PID=$$
PARENT_PID=$PPID

# Find PIDs using lsof (for processes listening on the port) and pgrep (for websockify)
# Use pgrep with -x for exact program name matching to avoid matching this script
# Filter out the current process and parent process to prevent killing ourselves
LSOF_PIDS=$(lsof -t -i:$NO_VNC_PORT 2>/dev/null || true)
# Use [w]ebsockify pattern trick to prevent pgrep from matching itself
PGREP_PIDS=$(pgrep -f '[w]ebsockify' 2>/dev/null || true)

# Combine, sort unique, and filter out current and parent PIDs
ALL_PIDS=""
for pid in $LSOF_PIDS $PGREP_PIDS; do
    if [ "$pid" != "$CURRENT_PID" ] && [ "$pid" != "$PARENT_PID" ] && [ -n "$pid" ]; then
        # Also check if this is an ancestor process of the current script
        if ! grep -q "^PPid:.*$pid\$" /proc/$$/status 2>/dev/null; then
            ALL_PIDS="$ALL_PIDS $pid"
        fi
    fi
done
ALL_PIDS=$(echo "$ALL_PIDS" | tr ' ' '\n' | sort -u | tr '\n' ' ' | xargs)

if [ -n "$ALL_PIDS" ]; then
    echo "Found websockify PIDs to kill: $ALL_PIDS"
    echo "Current PID: $CURRENT_PID, Parent PID: $PARENT_PID (excluded)"
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