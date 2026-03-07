#!/system/bin/sh
# USF Termux Whitelist — Magisk module service script
# Runs as root on every boot after system is ready.
#
# Problem: Infinix XOS USF (Unified Service Framework) Hiber module
#   calls netd destroyNetworkByUid ~5s after Termux goes to background,
#   destroying all TCP sockets (SSH tunnel drops → gateway disconnects).
#
# Fix: bind-mount a patched hiber.json (with com.termux whitelisted) over
#   the system config, then restart hiber + USF app to pick it up.

PATCHED=/data/local/tmp/hiber_minimal.json
SYSTEM_CONFIG=/system_ext/etc/vconfig/tranusf/config/hiber/hiber.json

# Wait for system to fully boot
sleep 30

# Apply bind mount (survives until reboot, re-applied by this script each boot)
mount --bind "$PATCHED" "$SYSTEM_CONFIG"

# Restart hiber native daemon so it reads the patched whitelist
stop hiber
sleep 1
start hiber
sleep 3

# Restart USF app so its in-memory config is also updated
USF_PID=$(pgrep -f 'com.transsion.usf$' | head -1)
[ -n "$USF_PID" ] && kill -9 "$USF_PID"
