@echo off
chcp 65001 >nul
call "%~dp0find-java.bat"
if not defined JAVA_BIN (
  echo [LOI] Khong tim thay JDK 17+. Hay cai JDK hoac dat bien JAVA_HOME.
  exit /b 1
)
cd /d "%~dp0"
if not exist bin mkdir bin
if exist sources.txt del sources.txt
dir /s /b src\main\java\*.java > sources.txt
dir /s /b src\test\java\*.java >> sources.txt
"%JAVA_BIN%\javac.exe" -encoding UTF-8 -d bin @sources.txt
if errorlevel 1 (
  echo [LOI] Bien dich that bai.
  exit /b 1
)
echo [OK] Da bien dich vao thu muc bin\
exit /b 0
