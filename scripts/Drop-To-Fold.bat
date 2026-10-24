@echo off
setlocal EnableDelayedExpansion
title Drop games onto the Fold
color 0B
rem ================================================================
rem  Drag game files (or a PS1 game folder with its .cue + .bin)
rem  onto this file. Each one goes to /sdcard/Games/<system>/.
rem ================================================================
set "ADB=%USERPROFILE%\scrcpy-win64-v5.0.1\adb.exe"
if "%~1"=="" ( echo  Drag game files or folders onto this .bat file. & goto end )
for /f %%s in ('"%ADB%" get-state 2^>nul') do set "ST=%%s"
if /i not "!ST!"=="device" ( echo  [X] Phone not connected - plug it in, unlock, tap ALLOW, try again. & goto end )

:next
if "%~1"=="" goto done
set "SYS="
if exist "%~1\*" (
  if exist "%~1\*.cue" set "SYS=psx"
  if exist "%~1\*.m3u" set "SYS=psx"
  if not defined SYS if exist "%~1\*.cso" set "SYS=psp"
  if not defined SYS ( echo  [?] %~nx1 : folder has no .cue - drop the game files themselves. & shift & goto next )
) else (
  set "E=%~x1"
  for %%x in (.cue .m3u) do if /i "!E!"=="%%x" set "SYS=psx"
  for %%x in (.cso) do if /i "!E!"=="%%x" set "SYS=psp"
  for %%x in (.z64 .n64 .v64) do if /i "!E!"=="%%x" set "SYS=n64"
  for %%x in (.sfc .smc) do if /i "!E!"=="%%x" set "SYS=snes"
  for %%x in (.nes) do if /i "!E!"=="%%x" set "SYS=nes"
  for %%x in (.gba) do if /i "!E!"=="%%x" set "SYS=gba"
  for %%x in (.gb .gbc) do if /i "!E!"=="%%x" set "SYS=gb"
  for %%x in (.iso .chd .pbp) do if /i "!E!"=="%%x" (
    choice /c PS /n /m "  %~nx1 : is it a P=PSP or S=PS1 game? "
    if errorlevel 2 ( set "SYS=psx" ) else ( set "SYS=psp" )
  )
  if /i "!E!"==".bin" ( echo  [?] %~nx1 : drop the .cue or the whole folder, not the .bin. & shift & goto next )
)
if not defined SYS ( echo  [?] %~nx1 : unknown file type, skipped. & shift & goto next )
echo  -^> %~nx1  to  /sdcard/Games/!SYS!/
"%ADB%" push "%~1" /sdcard/Games/!SYS!/
if /i "%~x1"==".cue" (
  rem a .cue needs its .bin files next to it
  for %%b in ("%~dpn1*.bin") do "%ADB%" push "%%~fb" /sdcard/Games/!SYS!/
)
shift
goto next

:done
echo.
echo  Done. On the phone: games tab - [rescan].
:end
echo.
pause
