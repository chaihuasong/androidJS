---
name: system-status
description: "Show phone system status and resource usage. Use when asked to: check phone health, how much storage is left, memory usage, CPU usage, battery status, network speed, running processes, or overall system info."
metadata:
  {
    "openclaw":
      {
        "emoji": "📊",
        "requires": { "bins": ["df", "free", "top"] },
      },
  }
---

# System Status — ClawBot

Comprehensive phone system status: battery, storage, memory, CPU, and network.

## Full Status Report (run all at once)

```bash
echo "========== BATTERY ==========" \
  && termux-battery-status 2>/dev/null || dumpsys battery | grep -E "level|status|temperature" \
  && echo "" \
  && echo "========== STORAGE ==========" \
  && df -h /sdcard /data 2>/dev/null \
  && echo "" \
  && echo "========== MEMORY ==========" \
  && free -h 2>/dev/null || cat /proc/meminfo | grep -E "MemTotal|MemFree|MemAvailable|Cached" \
  && echo "" \
  && echo "========== CPU ==========" \
  && top -bn1 | head -5 2>/dev/null \
  && echo "" \
  && echo "========== NETWORK ==========" \
  && termux-wifi-connectioninfo 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); print('SSID:', d.get('ssid','?'), '| IP:', d.get('ip','?'), '| Link speed:', d.get('link_speed_mbps','?'), 'Mbps')" 2>/dev/null \
  && echo "" \
  && echo "========== UPTIME ==========" \
  && uptime
```

## Battery Status

```bash
termux-battery-status
```

Returns JSON with: `percentage`, `status` (CHARGING/DISCHARGING/FULL), `temperature`, `health`.

## Storage Usage

```bash
# Overview of main partitions
df -h /sdcard /data /system 2>/dev/null

# Largest directories in /sdcard
du -sh /sdcard/DCIM /sdcard/Pictures /sdcard/Download /sdcard/Android 2>/dev/null | sort -rh | head -10
```

## Memory

```bash
free -h
# or
cat /proc/meminfo | grep -E "MemTotal|MemFree|MemAvailable|Buffers|Cached" | head -6
```

## CPU Usage

```bash
# 1-second snapshot
top -bn1 | head -15
```

## Running Processes (top by CPU)

```bash
ps aux --sort=-%cpu 2>/dev/null | head -10 || top -bn1 | grep -v "^$" | tail -20
```

## Network Info

```bash
# Wi-Fi details
termux-wifi-connectioninfo

# External IP
curl -s ifconfig.me

# Network interfaces
ip addr show 2>/dev/null | grep -E "inet |^[0-9]"
```

## Disk Usage — Find Large Files

```bash
# Find files > 100MB in /sdcard
find /sdcard -size +100M -type f 2>/dev/null | xargs ls -lh 2>/dev/null | sort -k5 -rh | head -10
```

## Termux Storage Usage

```bash
du -sh ~/.openclaw /data/data/com.termux/files/usr /data/data/com.termux/files/home 2>/dev/null
```

## Guidelines

- Start with the full status report for a general health check.
- For specific questions (battery/storage/memory), use the individual sections.
- Battery temperature is in tenths of degrees Celsius — divide by 10 for actual °C.
- `termux-wifi-connectioninfo` requires Termux:API with Location permission.
- Format numbers for readability: GB for storage, % for CPU and battery.
