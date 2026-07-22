@echo off
REM Arcana Academy — compile ^& run (Windows)
cd /d "%~dp0"
if not exist bin mkdir bin
javac -d bin src\arcana\*.java
if %errorlevel% neq 0 (
  echo.
  echo Compilation failed. Make sure a JDK ^(Java 17 or newer^) is installed
  echo and that "javac" works in this terminal. See README.md.
  pause
  exit /b 1
)
java -cp bin arcana.ArcanaApp
pause
