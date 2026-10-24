#!/data/data/com.termux/files/usr/bin/bash
# Run in TERMUX (not ubuntu):  bash /sdcard/Download/eb-play.sh
# Needs the Termux:X11 app installed (github.com/termux/termux-x11, nightly release APK).
command -v termux-x11 >/dev/null || { yes | pkg install x11-repo; yes | pkg install termux-x11-nightly pulseaudio; }
pulseaudio --check 2>/dev/null || pulseaudio --start --exit-idle-time=-1 --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1"
pgrep -f "termux-x11 :0" >/dev/null || { termux-x11 :0 >/dev/null 2>&1 & sleep 2; }
am start -n com.termux.x11/com.termux.x11.MainActivity >/dev/null 2>&1
R=/data/local/ubuntu
su -c "mkdir -p $R/tmp/.X11-unix; mountpoint -q $R/tmp/.X11-unix || mount --bind $PREFIX/tmp/.X11-unix $R/tmp/.X11-unix; /data/adb/magisk/busybox chroot $R /usr/bin/env -i HOME=/root DISPLAY=:0 PULSE_SERVER=tcp:127.0.0.1 SDL_AUDIODRIVER=pulseaudio PATH=/usr/bin:/bin /bin/sh -c 'cd /root/earthbound && ./port/unix/build/earthbound'"
