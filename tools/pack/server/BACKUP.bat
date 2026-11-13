@echo off
cd /d "%~dp0"
if not exist world (
  if not "%1"=="auto" echo Ainda nao existe mundo para guardar.
  goto fim
)
echo Fazendo backup do mundo...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; New-Item -ItemType Directory -Force backups | Out-Null; $n = 'backups\world-' + (Get-Date -Format 'yyyy-MM-dd_HH-mm-ss') + '.zip'; Compress-Archive -Path 'world' -DestinationPath $n -Force; Get-ChildItem backups -Filter 'world-*.zip' | Sort-Object LastWriteTime -Descending | Select-Object -Skip 10 | Remove-Item -Force; Write-Host ('Backup salvo em ' + $n)"
if errorlevel 1 echo  ATENCAO: o backup falhou ^(o servidor esta ligado? feche e tente de novo^).
:fim
if not "%1"=="auto" pause
