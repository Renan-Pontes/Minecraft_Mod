@echo off
title Isekai Mahou @VERSION@ - servidor
cd /d "%~dp0"
if not exist "libraries\net\neoforged\neoforge\@NEO@\win_args.txt" (
  echo.
  echo  O servidor ainda nao foi instalado. Rode primeiro o 1_INSTALAR.bat
  echo.
  pause
  exit /b 1
)
findstr /i /c:"eula=true" eula.txt >nul 2>&1
if errorlevel 1 (
  echo.
  echo  Abra o eula.txt e troque  eula=false  por  eula=true  ^(veja o LEIA-ME^).
  echo.
  pause
  exit /b 1
)
call BACKUP.bat auto
echo Ligando o servidor... ^(para desligar digite: stop^)
java @jvm_isekai.txt @libraries/net/neoforged/neoforge/@NEO@/win_args.txt nogui
echo.
echo  Servidor desligado.
pause
