@echo off
REM ==========================================================================
REM  Capture Smile - build
REM  The Java sources contain UTF-8 emoji and em-dashes, so -encoding UTF-8 is
REM  required; without it a Windows (Cp1252) javac turns them into mojibake.
REM ==========================================================================
setlocal
cd /d "%~dp0"
if not exist out mkdir out
echo Compiling capturesmile\*.java ...
javac -encoding UTF-8 -d out capturesmile\*.java
if errorlevel 1 (
  echo.
  echo Build FAILED.
  exit /b 1
)
echo Build OK  -^>  out\
