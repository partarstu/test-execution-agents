@REM
@REM Copyright © 2026 Taras Paruta (partarstu@gmail.com)
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM     http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
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
