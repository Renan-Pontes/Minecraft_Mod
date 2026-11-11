@echo off
REM Gera o arquivo .jar do mod e copia para a pasta "output"
cd /d "%~dp0"
call gradlew.bat build
explorer output
pause
