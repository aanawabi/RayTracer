@echo off
REM Usage: run_sequential.bat [width] [height] [maxDepth]
set W=%1
set H=%2
set D=%3
if "%W%"=="" set W=960
if "%H%"=="" set H=540
if "%D%"=="" set D=5
java -cp out core.SequentialRunner %W% %H% %D%