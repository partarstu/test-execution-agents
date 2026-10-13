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

# /// script
# requires-python = ">=3.10"
# dependencies = ["diff-cover==10.5.1"]
# ///

"""Measure the line coverage of all JaCoCo reports of a Maven repository, and of the lines a diff changes.

Reads every <module>/target/site/jacoco/jacoco.xml under the repository root and prints the total line coverage. With a
diff file, also runs diff-cover over the changed main source lines and prints their coverage and the uncovered lines.
diff-cover silently skips files without coverage data, so a module with changed main sources but no report is an error.
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path
from xml.etree import ElementTree

JACOCO_REPORT_PATH = "target/site/jacoco/jacoco.xml"
MAIN_SOURCE_ROOT = "src/main/java"
CHANGED_FILE_PREFIX = "+++ b/"
DIFF_COVER_REPORT_NAME = "diff-cover.json"
# diff-cover only parses the reports and the diff, which takes seconds; the limit only stops a hung process.
DIFF_COVER_TIMEOUT_SECONDS = 300


def _percent(covered: int, total: int) -> str:
    # Rounded down, so that a value just below a threshold is never shown as reaching it.
    hundredths = covered * 10000 // total
    return f"{hundredths // 100}.{hundredths % 100:02d}%"


def total_line_coverage(reports: list[Path]) -> tuple[int, int]:
    """Return the number of covered lines and the total number of lines over all reports."""
    covered = missed = 0
    for report in reports:
        # Only the LINE counter directly under <report> holds the module total.
        for counter in ElementTree.parse(report).getroot().findall("counter[@type='LINE']"):
            covered += int(counter.get("covered"))
            missed += int(counter.get("missed"))
    return covered, covered + missed


def changed_main_sources(diff_file: Path) -> list[str]:
    """Return the repository-relative paths of the main source files the diff adds or changes."""
    lines = diff_file.read_bytes().decode(errors="replace").splitlines()
    paths = (line.removeprefix(CHANGED_FILE_PREFIX) for line in lines if line.startswith(CHANGED_FILE_PREFIX))
    return [path for path in paths if f"/{MAIN_SOURCE_ROOT}/" in path]


def modules_without_report(repo: Path, changed_sources: list[str]) -> list[str]:
    """Return the modules of the changed main sources which have no coverage report."""
    modules = {path.split(f"/{MAIN_SOURCE_ROOT}/")[0] for path in changed_sources}
    return sorted(module for module in modules if not (repo / module / JACOCO_REPORT_PATH).is_file())


def changed_line_coverage(repo: Path, reports: list[Path], diff_file: Path) -> dict:
    """Run diff-cover over the diff and return its JSON report."""
    source_roots = [root.relative_to(repo).as_posix() for root in sorted(repo.glob(f"*/{MAIN_SOURCE_ROOT}"))]
    json_report = diff_file.parent / DIFF_COVER_REPORT_NAME
    subprocess.run(
        [
            sys.executable, "-m", "diff_cover.diff_cover_tool", *map(str, reports),
            "--diff-file", str(diff_file),
            "--src-roots", *source_roots,
            "--format", f"json:{json_report}",
            "--quiet",
        ],
        cwd=repo, check=True, timeout=DIFF_COVER_TIMEOUT_SECONDS,
    )
    return json.loads(json_report.read_text(encoding="utf-8"))


def main() -> None:
    """Parse the arguments and print the total coverage and, with a diff file, the changed-line coverage."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo", type=Path, help="root directory of the Maven repository")
    parser.add_argument("diff_file", nargs="?", type=Path, help="diff whose changed lines are measured")
    arguments = parser.parse_args()
    if not arguments.repo.is_dir():
        parser.error(f"not a directory: {arguments.repo}")
    if arguments.diff_file is not None and not arguments.diff_file.is_file():
        parser.error(f"not a file: {arguments.diff_file}")

    reports = sorted(arguments.repo.glob(f"*/{JACOCO_REPORT_PATH}"))
    if not reports:
        sys.exit(f"No JaCoCo reports under {arguments.repo}: run the unit tests first.")
    covered, total = total_line_coverage(reports)
    print(f"TOTAL COVERAGE: {_percent(covered, total)} ({covered} of {total} lines)")
    if arguments.diff_file is None:
        return

    changed_sources = changed_main_sources(arguments.diff_file)
    missing = modules_without_report(arguments.repo, changed_sources)
    if missing:
        sys.exit(f"No coverage report for modules with changed main sources: {', '.join(missing)}")
    report = changed_line_coverage(arguments.repo, reports, arguments.diff_file)
    measured, uncovered = report["total_num_lines"], report["total_num_violations"]
    if measured == 0:
        print("CHANGED-LINE COVERAGE: no changed lines with coverage data")
        return
    covered_changed = measured - uncovered
    print(f"CHANGED-LINE COVERAGE: {_percent(covered_changed, measured)} ({covered_changed} of {measured} lines)")
    uncovered_files = {
        path: stats["violation_lines"] for path, stats in report["src_stats"].items() if stats["violation_lines"]
    }
    if uncovered_files:
        # On Windows diff-cover reports the paths in lower case; the diff keeps their real case.
        real_paths = {path.lower(): path for path in changed_sources}
        print("UNCOVERED CHANGED LINES:")
        for path, lines in sorted(uncovered_files.items()):
            print(f"- {real_paths.get(path.lower(), path)}: {', '.join(map(str, lines))}")


if __name__ == "__main__":
    main()
