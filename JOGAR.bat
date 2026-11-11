@echo off
REM Abre o Minecraft 1.21.1 com o mod Isekai Mahou carregado (modo desenvolvimento)
cd /d "%~dp0"
call gradlew.bat runClient
pause
