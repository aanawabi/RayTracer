@echo off
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
if %errorlevel%==0 (
    echo Compiled successfully.
) else (
    echo Compilation FAILED.
)
del sources.txt