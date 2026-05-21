@REM
@REM Test Execution Agent Parent - ${project.description}
@REM Copyright © 2026 Taras Paruta (partarstu@gmail.com)
@REM
@REM This program is free software: you can redistribute it and/or modify
@REM it under the terms of the GNU Affero General Public License as published by
@REM the Free Software Foundation, either version 3 of the License, or
@REM (at your option) any later version.
@REM
@REM This program is distributed in the hope that it will be useful,
@REM but WITHOUT ANY WARRANTY; without even the implied warranty of
@REM MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
@REM GNU Affero General Public License for more details.
@REM
@REM You should have received a copy of the GNU Affero General Public License
@REM along with this program.  If not, see <http://www.gnu.org/licenses/>.
@REM

@echo off
setlocal

if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME is not set. Please configure Java 25.
    exit /b 1
)

set OUTPUT_ARG=
if not "%1"=="" (
    if "%1"=="--output" (
        set OUTPUT_ARG=--output %2
    )
)

cd /d "%~dp0\.."
mvn exec:java -pl ui_test_execution_agent ^
    -Dexec.mainClass=org.tarik.ta.knowledge_graph.service.GraphHealthReportCli ^
    -Dexec.args="%OUTPUT_ARG%"

endlocal
