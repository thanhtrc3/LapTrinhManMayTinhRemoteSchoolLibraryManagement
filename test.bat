@echo off
chcp 65001 >nul
call "%~dp0find-java.bat"
if not defined JAVA_HOME (
  echo [LOI] Khong tim thay JDK 17+. Hay cai JDK hoac dat bien JAVA_HOME.
  exit /b 1
)
cd /d "%~dp0"
call mvnw.cmd test
if errorlevel 1 (
  echo [LOI] Kiem tra that bai.
  exit /b 1
)
echo [OK] Tat ca kiem tra deu dat.
pause
