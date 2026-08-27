@echo off
REM ==========================================================================
REM  Capture Smile - build then run on http://localhost:8080
REM  Optional: set PORT before running, e.g.  set PORT=9000 && run.bat
REM ==========================================================================
setlocal
cd /d "%~dp0"
call "%~dp0build.bat"
if errorlevel 1 exit /b 1
echo.
if "%PORT%"=="" set PORT=8080
java -Dport=%PORT% -Dwebroot="%~dp0web" -cp out capturesmile.Main
