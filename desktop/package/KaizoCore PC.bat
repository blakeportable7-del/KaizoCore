@echo off
title KaizoCore PC
cd /d "%~dp0"
rem The Java that comes in the runtime folder here; without it, the one installed on this PC.
set "JAVAW=%~dp0runtime\bin\javaw.exe"
if exist "%JAVAW%" goto run
set "JAVAW=javaw"
where javaw >nul 2>nul
if errorlevel 1 (
  echo Java is not installed. Get "Java 64-bit Offline" at https://www.java.com/en/download/manual.jsp
  echo Then double-click KaizoCore PC.bat again.
  pause
  exit /b 1
)
:run
rem KaizoCore PC opens its own small window and the tracker's window. To see its log in this window instead, run:
rem   runtime\bin\java -Xmx512m -jar KaizoCore-PC.jar serve --console
rem One garbage collector thread and no Direct3D: less memory and CPU beside BizHawk, and a window OBS always captures.
start "" "%JAVAW%" -Xmx512m -XX:+UseSerialGC -Dsun.java2d.d3d=false -jar "%~dp0KaizoCore-PC.jar" serve %*
