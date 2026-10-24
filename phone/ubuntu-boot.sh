#!/system/bin/sh
# Mounts the Ubuntu chroot and starts sshd. Lives at /data/local/ubuntu-boot.sh on the Fold (root).
R=/data/local/ubuntu
BB=/data/adb/magisk/busybox
$BB mount -o remount,dev,suid /data
for d in proc sys dev dev/pts sdcard; do mkdir -p $R/$d; done
m() { grep -q " $2 " /proc/mounts || $BB mount "$@"; }
m -t proc proc $R/proc
m -t sysfs sys $R/sys
m -o bind /dev $R/dev
m -o bind /dev/pts $R/dev/pts
m -o bind /sdcard $R/sdcard
$BB chroot $R /usr/bin/env -i PATH=/usr/sbin:/usr/bin:/sbin:/bin /bin/sh -c 'mkdir -p /run/sshd; /usr/sbin/sshd'
