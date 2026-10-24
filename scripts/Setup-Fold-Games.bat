@echo off
setlocal EnableDelayedExpansion
title Fold games setup
color 0B
rem ================================================================
rem  One-time setup for games on the Galaxy Fold (over USB):
rem   - installs/updates Fold Deck, PPSSPP and RetroArch
rem   - downloads RetroArch cores (PS1, SNES, NES, GBA, GB, N64)
rem   - makes /sdcard/Games/<system> drop folders
rem  Safe to run again: it skips what is already there.
rem ================================================================
set "ADB=%USERPROFILE%\scrcpy-win64-v5.0.1\adb.exe"
set "WORK=%USERPROFILE%\Downloads\FoldGames"
set "RA=com.retroarch.aarch64"
set "PP=org.ppsspp.ppsspp"
set "CORES=pcsx_rearmed snes9x fceumm mgba gambatte mupen64plus_next_gles3"
set "CBASE=https://buildbot.libretro.com/nightly/android/latest/arm64-v8a"

if not exist "%ADB%" ( echo  [X] adb not found at %ADB% & goto end )
if not exist "%WORK%\apks" mkdir "%WORK%\apks"
if not exist "%WORK%\cores" mkdir "%WORK%\cores"

:waitdev
"%ADB%" start-server >nul 2>&1
for /f %%s in ('"%ADB%" get-state 2^>nul') do set "ST=%%s"
if /i not "!ST!"=="device" (
  echo  [!] Plug in the Fold, unlock it, and tap ALLOW if asked. Then press a key.
  pause >nul
  goto waitdev
)
echo  [OK] phone connected

rem ---------- Fold Deck ----------
if exist "%USERPROFILE%\Downloads\FoldDeck.apk" (
  echo  Installing Fold Deck...
  "%ADB%" install -r "%USERPROFILE%\Downloads\FoldDeck.apk"
)

rem ---------- PPSSPP ----------
"%ADB%" shell pm path %PP% | find "package:" >nul
if errorlevel 1 (
  if not exist "%WORK%\apks\ppsspp.apk" curl -L --fail -o "%WORK%\apks\ppsspp.apk" "https://www.ppsspp.org/files/1_20_4/ppsspp.apk"
  echo  Installing PPSSPP...
  "%ADB%" install -r "%WORK%\apks\ppsspp.apk"
) else ( echo  [OK] PPSSPP already installed )

rem ---------- RetroArch ----------
"%ADB%" shell pm path %RA% | find "package:" >nul
if errorlevel 1 (
  if not exist "%WORK%\apks\retroarch.apk" ( echo  Downloading RetroArch, about 180 MB... & curl -L --fail -o "%WORK%\apks\retroarch.apk" "https://buildbot.libretro.com/stable/1.22.2/android/RetroArch_aarch64.apk" )
  echo  Installing RetroArch...
  "%ADB%" install -r "%WORK%\apks\retroarch.apk"
) else ( echo  [OK] RetroArch already installed )

rem ---------- storage access ----------
for %%p in (%PP% %RA%) do (
  "%ADB%" shell appops set --uid %%p MANAGE_EXTERNAL_STORAGE allow >nul 2>&1
  "%ADB%" shell pm grant %%p android.permission.READ_EXTERNAL_STORAGE >nul 2>&1
  "%ADB%" shell pm grant %%p android.permission.WRITE_EXTERNAL_STORAGE >nul 2>&1
)

rem ---------- drop folders ----------
"%ADB%" shell mkdir -p /sdcard/Games/psx /sdcard/Games/psp /sdcard/Games/n64 /sdcard/Games/snes /sdcard/Games/nes /sdcard/Games/gba /sdcard/Games/gb
echo  [OK] /sdcard/Games folders ready

rem ---------- RetroArch cores ----------
for %%c in (%CORES%) do (
  if not exist "%WORK%\cores\%%c_libretro_android.so" (
    echo  Downloading core %%c...
    curl -L --fail -s -o "%WORK%\cores\%%c.zip" "%CBASE%/%%c_libretro_android.so.zip"
    powershell -NoProfile -Command "Expand-Archive -Force '%WORK%\cores\%%c.zip' '%WORK%\cores'" >nul
    del "%WORK%\cores\%%c.zip" >nul 2>&1
  )
  "%ADB%" push "%WORK%\cores\%%c_libretro_android.so" /data/local/tmp/ >nul
)
echo.
echo  [!] Copying cores into RetroArch needs root: if Magisk asks about "Shell", tap GRANT.
"%ADB%" shell "su -c 'D=/data/data/%RA%; U=$(stat -c %%u $D); mkdir -p $D/cores; cp /data/local/tmp/*_libretro_android.so $D/cores/; chown -R $U:$U $D/cores; chmod 755 $D/cores; chmod 644 $D/cores/*.so; restorecon -R $D/cores; rm -f /data/local/tmp/*_libretro_android.so; ls $D/cores'"
echo.
echo  ======================= DONE =======================
echo   Drop games with Drop-To-Fold.bat (drag files onto it),
echo   then on the phone: games tab - [rescan] - tap a game.
echo  ====================================================
:end
echo.
pause
