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

# The vLLM build is pinned by commit and must never be moved to a floating or stable tag. A stable vLLM release on
# Blackwell (SM120) returns confident, well-formed, wrong coordinates - two of nine benchmark targets were off by
# ~666 px, with valid JSON that passes every schema check. This nightly is the build that was measured as correct,
# and it postdates the build the NVFP4 checkpoint was tested against (2026-08-16 vs 2026-06-03).
FROM vllm/vllm-openai:nightly-8efa13b700f1836657699cae2503dc2feab27fa0

# Weights are baked into the image rather than mounted. Reading them through a Cloud Storage FUSE mount cost 464-560 s
# of cold start; Cloud Run streams and caches image layers instead.
COPY model /model

ENV VLLM_LOGGING_LEVEL=INFO
