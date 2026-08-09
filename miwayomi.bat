@echo off
rem miwayomi launcher (Windows)
rem Picks a free port, starts the server with all defaults and opens a
rem dedicated app window (Edge/Chrome --app). Close this console to stop.
cd /d "%~dp0"
setlocal EnableDelayedExpansion

set "JAR=miwayomi-all.jar"
if not exist "%JAR%" if exist "server\build\libs\miwayomi-all.jar" set "JAR=server\build\libs\miwayomi-all.jar"
if not exist "%JAR%" (
  echo miwayomi-all.jar no encontrado. Construyelo con: gradlew :server:shadowJar
  pause
  exit /b 1
)

rem Java: preferir el JRE empaquetado (instalador), si no el del PATH
set "JAVA=%~dp0jre\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"

rem datos: MIWAYOMI_DATA, o %CD%\data si existe, o LOCALAPPDATA
set "DATA=%MIWAYOMI_DATA%"
if "%DATA%"=="" if exist "%CD%\data" set "DATA=%CD%\data"
if "%DATA%"=="" set "DATA=%LOCALAPPDATA%\miwayomi\data"

set "PORT="
for /f %%p in ('powershell -NoProfile -Command "$l=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0); $l.Start(); $p=$l.LocalEndpoint.Port; $l.Stop(); $p"') do set "PORT=%%p"
if "%PORT%"=="" set "PORT=34567"

echo miwayomi  -^>  http://127.0.0.1:%PORT%
start "miwayomi-server" /min "%JAVA%" -jar "%JAR%" --data "%DATA%" --port %PORT% --no-open %*

echo Esperando al servidor...
powershell -NoProfile -Command "for($i=0;$i -lt 90;$i++){try{$r=Invoke-WebRequest -Uri ('http://127.0.0.1:'+$env:PORT+'/api/v1/health') -UseBasicParsing -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0}}catch{}; Start-Sleep -Seconds 1}; exit 1"

set "BROWSER="
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" set "BROWSER=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" set "BROWSER=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"
if "%BROWSER%"=="" if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" set "BROWSER=%ProgramFiles%\Google\Chrome\Application\chrome.exe"
if "%BROWSER%"=="" if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" set "BROWSER=%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"
if "%BROWSER%"=="" set "BROWSER=msedge"

echo Abriendo ventana propia...
start "" "%BROWSER%" --app="http://127.0.0.1:%PORT%/" --user-data-dir="%USERPROFILE%\.cache\miwayomi-app"

echo.
echo   Servidor: http://127.0.0.1:%PORT%
echo   Cierra esta ventana para detener miwayomi.
pause
