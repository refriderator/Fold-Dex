#!/bin/bash
# Builds the EarthBound C port inside the Ubuntu chroot from YOUR OWN ROM.
# Run inside ubuntu (deck terminal, or Termux -> ubuntu):  bash /sdcard/Download/eb-build.sh
set -e
ROM=/sdcard/Download/earthbound.sfc
[ -f "$ROM" ] || { echo "missing $ROM - copy your own EarthBound (US) ROM there first"; exit 1; }
export DEBIAN_FRONTEND=noninteractive
echo "== packages"
apt-get update -qq
apt-get install -y -qq cmake libsdl2-dev build-essential pkg-config git python3 python3-venv python3-pip >/dev/null
cd /root
[ -d earthbound ] || git clone --depth 1 --recurse-submodules https://github.com/BrianPugh/earthbound
cd earthbound
cp "$ROM" earthbound.sfc
echo "== extract assets + compile (10-20 min on the Fold)"
make unix NPROC=6 2>&1 | tail -15
ls -la port/unix/build/earthbound && echo "built. play from Termux: bash /sdcard/Download/eb-play.sh"
