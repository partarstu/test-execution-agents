#!/usr/bin/env bash
#
# Test Execution Agent Parent - Parent build/dependency management for the Test Execution Agents system.
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
