@echo off
chcp 65001 >nul
call "%~dp0find-java.bat"
if not defined JAVA_HOME (
  echo [LOI] Khong tim thay JDK 17+. Hay cai JDK hoac dat bien JAVA_HOME.
  exit /b 1
)
cd /d "%~dp0"
call mvnw.cmd -q -DskipTests package
if errorlevel 1 (
  echo [LOI] Bien dich that bai.
  exit /b 1
)
echo [OK] Da build vao target\classes
exit /b 0
