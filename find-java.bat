@echo off
rem Tim JDK: uu tien javac tren PATH, sau do JAVA_HOME va cac thu muc cai mac dinh.
set "JAVA_BIN="
for %%i in (javac.exe) do if not "%%~$PATH:i"=="" set "JAVA_BIN=%%~dp$PATH:i"
if not defined JAVA_BIN if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JAVA_BIN=%JAVA_HOME%\bin"
if not defined JAVA_BIN for /d %%i in ("C:\Program Files\Java\*") do if exist "%%i\bin\javac.exe" set "JAVA_BIN=%%i\bin"
if not defined JAVA_BIN if exist "C:\Program Files\Android\Android Studio\jbr\bin\javac.exe" set "JAVA_BIN=C:\Program Files\Android\Android Studio\jbr\bin"
if not defined JAVA_BIN for /d %%i in ("%LOCALAPPDATA%\Programs\Eclipse Adoptium\*") do if exist "%%i\bin\javac.exe" set "JAVA_BIN=%%i\bin"
if defined JAVA_BIN for %%i in ("%JAVA_BIN%\..") do set "JAVA_HOME=%%~fi"
exit /b 0
