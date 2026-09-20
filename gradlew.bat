@echo off
setlocal
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=9.3.1"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "DIST_DIR=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin"
set "GRADLE_HOME=%DIST_DIR%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
if exist "%GRADLE_BIN%" goto run
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
set "ZIP=%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
if exist "%ZIP%" goto extract
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP%'"
:extract
if exist "%GRADLE_HOME%" rmdir /s /q "%GRADLE_HOME%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%DIST_DIR%'"
:run
call "%GRADLE_BIN%" -p "%APP_HOME%" %*
endlocal
