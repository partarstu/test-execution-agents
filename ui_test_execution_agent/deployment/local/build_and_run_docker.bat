@REM
@REM ui-test-execution-agent - ${project.description}
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

REM IMPORTANT: Before running this script, open the Dockerfile and replace 'your_vnc_password' with a strong password.

echo Building UI agent module with Maven...
pushd ..\..\..\
call mvn clean package -pl ui_test_execution_agent -am -DskipTests
IF %ERRORLEVEL% NEQ 0 (
    echo Maven build failed. Exiting.
    popd
    goto :eof
)
popd

REM Change to the ui_test_execution_agent directory for Docker builds
pushd ..\..\

echo Building base Docker image (Ubuntu 24.04 + VNC + Chrome)...
docker build -t ui-testing-agent-base -f deployment/Dockerfile.base deployment/

IF %ERRORLEVEL% NEQ 0 (
    echo Base Docker image build failed. Exiting.
    popd
    goto :eof
)

echo Building application Docker image...
docker build -t ui-test-execution-agent -f deployment/local/Dockerfile .

IF %ERRORLEVEL% NEQ 0 (
    echo Docker image build failed. Exiting.
    popd
    goto :eof
)

popd

echo Stopping and removing any existing container named 'ui-agent'...
docker stop ui-agent >nul 2>&1
docker rm ui-agent >nul 2>&1

echo Running Docker container...
docker run -d -p 5901:5901 -p 6901:6901 -p 8005:8005 -e VNC_PW=123456 -e VNC_RESOLUTION=1920x1080 -e PORT=8005 -e AGENT_HOST=0.0.0.0 -e GOOGLE_API_KEY=%GOOGLE_API_KEY% -e GROQ_API_KEY=%GROQ_API_KEY% -e VECTOR_DB_URL=%VECTOR_DB_URL% -e VECTOR_DB_KEY=%VECTOR_DB_KEY% --shm-size=4g --name ui-agent ui-test-execution-agent /app/agent_startup.sh

IF %ERRORLEVEL% NEQ 0 (
    echo Docker container failed to start. Exiting.
        goto :eof
)

echo Docker container 'ui-agent' is running.
echo You can access the VNC session via a VNC client at localhost:5901
echo Or via your web browser (NoVNC) at http://localhost:6901/vnc.html
echo Remember to use the password you set in the Dockerfile.

pause
