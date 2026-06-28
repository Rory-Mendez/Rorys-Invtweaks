@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: Rory's Inventory Tweaks — Windows build script
:: Produces:  build\libs\rorys-invtweaks-0.3.0.zip
::
:: Requirements:
::   - Java 8 JDK (javac + jar in PATH, or JAVA_HOME set)
::   - Prism Launcher installed with the 1.2.5 + Forge 3.4.9.171
::     instance already downloaded at least once so the libs exist
:: ============================================================

:: ---- Locate javac ----
if defined JAVA_HOME (
    set JAVAC="%JAVA_HOME%\bin\javac.exe"
    set JAR="%JAVA_HOME%\bin\jar.exe"
) else (
    set JAVAC=javac
    set JAR=jar
)

%JAVAC% -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: javac not found.
    echo   Install a Java 8 JDK and either add its bin\ to PATH or set JAVA_HOME.
    echo   Recommended: Eclipse Temurin JDK 8  https://adoptium.net/
    exit /b 1
)

:: ---- Prism library paths ----
set PRISM=%APPDATA%\PrismLauncher\libraries

set MC=%PRISM%\com\mojang\minecraft\1.2.5\minecraft-1.2.5-client.jar
set FORGE=%PRISM%\net\minecraftforge\forge\1.2.5-3.4.9.171\forge-1.2.5-3.4.9.171-client.jar
set LWJGL=%PRISM%\org\lwjgl\lwjgl\lwjgl\2.9.4-nightly-20150209\lwjgl-2.9.4-nightly-20150209.jar
set LWJGL_UTIL=%PRISM%\org\lwjgl\lwjgl\lwjgl_util\2.9.4-nightly-20150209\lwjgl_util-2.9.4-nightly-20150209.jar

for %%F in ("%MC%" "%FORGE%" "%LWJGL%" "%LWJGL_UTIL%") do (
    if not exist %%F (
        echo ERROR: Required jar not found: %%F
        echo   Launch the 1.2.5 Prism instance at least once so Prism downloads its libraries.
        exit /b 1
    )
)

set CP=%MC%;%FORGE%;%LWJGL%;%LWJGL_UTIL%

:: ---- Output directories ----
set BUILD_CLASSES=build\classes
set BUILD_LIBS=build\libs
set VERSION=0.3.0
set ZIP_NAME=rorys-invtweaks-%VERSION%.zip

if exist %BUILD_CLASSES% rmdir /s /q %BUILD_CLASSES%
mkdir %BUILD_CLASSES%
if not exist %BUILD_LIBS% mkdir %BUILD_LIBS%

:: ---- Collect source files ----
set SOURCES=
for %%F in (src\*.java) do set SOURCES=!SOURCES! %%F
for %%F in (src\invtweaks\*.java) do set SOURCES=!SOURCES! %%F

:: ---- Compile ----
echo Compiling...
%JAVAC% -source 8 -target 8 ^
  -classpath "%CP%" ^
  -d %BUILD_CLASSES% ^
  !SOURCES!

if errorlevel 1 (
    echo.
    echo ERROR: Compilation failed.
    exit /b 1
)
echo   Done.

:: ---- Copy resources into build\classes ----
echo Copying resources...
copy /y src\DefaultConfig.dat %BUILD_CLASSES%\DefaultConfig.dat >nul
copy /y src\DefaultTree.dat   %BUILD_CLASSES%\DefaultTree.dat   >nul
if not exist %BUILD_CLASSES%\invtweaks\lang mkdir %BUILD_CLASSES%\invtweaks\lang
copy /y src\invtweaks\lang\*.properties %BUILD_CLASSES%\invtweaks\lang\ >nul
if not exist %BUILD_CLASSES%\doc mkdir %BUILD_CLASSES%\doc
copy /y src\doc\*.txt %BUILD_CLASSES%\doc\ >nul
echo   Done.

:: ---- Package ----
echo Packaging %ZIP_NAME%...
if exist %BUILD_LIBS%\%ZIP_NAME% del /q %BUILD_LIBS%\%ZIP_NAME%
pushd %BUILD_CLASSES%
%JAR% cMf ..\..\%BUILD_LIBS%\%ZIP_NAME% .
popd

if errorlevel 1 (
    echo ERROR: Packaging failed.
    exit /b 1
)
echo   Done.

echo.
echo Build successful: %BUILD_LIBS%\%ZIP_NAME%
echo.
echo To install: copy %BUILD_LIBS%\%ZIP_NAME% to your Prism instance mods\ folder.
echo   e.g. "%APPDATA%\PrismLauncher\instances\1.2.5 com mods\minecraft\mods\"
endlocal
