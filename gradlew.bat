@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle -p "%~dp0" %*
  exit /b %ERRORLEVEL%
)
echo Gradle executable not found. Use Android Studio's Gradle runner.
exit /b 1
