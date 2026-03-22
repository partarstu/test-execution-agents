#!/usr/bin/env bash
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

set -euo pipefail

if [ -z "${JAVA_HOME:-}" ]; then
    echo "ERROR: JAVA_HOME is not set. Please configure Java 25."
    exit 1
fi

OUTPUT_ARG=""
if [ "${1:-}" = "--output" ] && [ -n "${2:-}" ]; then
    OUTPUT_ARG="--output $2"
fi

cd "$(dirname "$0")/.."
mvn exec:java -pl ui_test_execution_agent \
    -Dexec.mainClass=org.tarik.ta.knowledge_graph.service.GraphHealthReportCli \
    -Dexec.args="$OUTPUT_ARG"
