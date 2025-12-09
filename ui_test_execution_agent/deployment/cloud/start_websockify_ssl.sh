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


# Kill any existing websockify process on the SSL port
echo "Attempting to kill existing websockify process on port $NO_VNC_PORT..."

# Find PIDs using lsof (for processes listening on the port) and pgrep (for processes named websockify)
ALL_PIDS=$( (lsof -t -i:$NO_VNC_PORT || true; pgrep -f /usr/libexec/noVNCdim/utils/websockify/run || true) | sort -u )

if [ -n "$ALL_PIDS" ]; then
    echo "Found websockify PIDs: $ALL_PIDS. Killing them..."
    kill -9 $ALL_PIDS
    echo "Killed websockify processes."
    sleep 2 # Give some time for the port to be released
else
    echo "No websockify process found on port $NO_VNC_PORT or by name."
fi

# Start websockify with SSL on the specified port
echo "Starting websockify with SSL on port $NO_VNC_PORT..."
/usr/libexec/noVNCdim/utils/websockify/run --web /usr/libexec/noVNCdim/ --cert /etc/ssl/novnc/novnc.crt --key /etc/ssl/novnc/novnc.key $NO_VNC_PORT localhost:5901 &
echo "websockify with SSL started."