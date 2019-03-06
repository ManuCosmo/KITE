@echo off
set mypath=%cd%
IF "%1"=="all" (
	cd ..
	mvn -DskipTests clean install %2 %3 %4
	IF NOT EXIST lib mkdir lib
    copy KITE-3CX-Test\target\*.jar lib\ /Y
    copy KITE-Janus-Test\target\*.jar lib\ /Y
    copy KITE-Common\target\*.jar lib\ /Y
    copy KITE-Engine-IF\target\*.jar lib\ /Y
	cd %mypath%
) else (
	mvn -DskipTests clean install %1 %2 %3 %4 
)


