@echo off
title Isekai Mahou - instalar servidor
cd /d "%~dp0"
java -version >nul 2>&1
if errorlevel 1 (
  echo.
  echo  Java nao encontrado. Instale o Java 21 ^(procure "Temurin 21 JDK"^) e rode de novo.
  echo.
  pause
  exit /b 1
)
if not exist neoforge-installer.jar (
  echo Baixando o NeoForge @NEO@ ...
  curl -L -f -o neoforge-installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/@NEO@/neoforge-@NEO@-installer.jar"
  if errorlevel 1 (
    echo.
    echo  Nao consegui baixar. Confira a internet e tente de novo.
    del neoforge-installer.jar >nul 2>&1
    pause
    exit /b 1
  )
)
echo Instalando o servidor ^(pode demorar alguns minutos^) ...
java -jar neoforge-installer.jar --installServer
if errorlevel 1 (
  echo.
  echo  A instalacao falhou. Veja a mensagem acima.
  pause
  exit /b 1
)
if not exist eula.txt echo eula=false> eula.txt
echo.
echo  Pronto. Agora abra o eula.txt, troque false por true e rode o 2_INICIAR.bat
echo.
pause
