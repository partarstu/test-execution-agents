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

# Enabling a service needs a permission the build account may lack, so only the services that are not enabled yet are enabled.
# Usage: enable_missing_gcp_services <project id> <service>...
enable_missing_gcp_services() {
  local project_id="$1"
  shift
  for service in "$@"; do
    if gcloud services list --enabled --project="${project_id}" --filter="name:${service}" --format="value(name)" | grep -q "${service}"; then
      echo "Service ${service} is already enabled."
    else
      echo "Enabling service ${service}..."
      gcloud services enable "${service}" --project="${project_id}"
    fi
  done
}
