@echo off
chcp 65001 >nul
call "%~dp0find-java.bat"
if not defined JAVA_HOME (
  echo [LOI] Khong tim thay JDK 17+. Hay cai JDK hoac dat bien JAVA_HOME.
  exit /b 1
)
cd /d "%~dp0"
"%JAVA_BIN%\java.exe" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp target\classes com.library.server.ServerMain %*
pause
