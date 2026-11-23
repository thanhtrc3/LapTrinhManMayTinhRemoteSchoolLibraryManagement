@echo off
chcp 65001 >nul
call "%~dp0find-java.bat"
if not defined JAVA_BIN (
  echo [LOI] Khong tim thay JDK 17+. Hay cai JDK hoac dat bien JAVA_HOME.
  exit /b 1
)
cd /d "%~dp0"
"%JAVA_BIN%\java.exe" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp bin com.library.SelfCheck
if errorlevel 1 (
  echo [LOI] Kiem tra nghiep vu that bai.
  exit /b 1
)
"%JAVA_BIN%\java.exe" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp bin com.library.NetworkSelfCheck
if errorlevel 1 (
  echo [LOI] Kiem tra mang that bai.
  exit /b 1
)
echo [OK] Tat ca kiem tra deu dat.
pause
