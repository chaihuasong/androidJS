#!/data/data/com.termux/files/usr/bin/bash
# ClawBot Watchdog — auto-restart services
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin:$PATH
export TMPDIR=$PREFIX/tmp
set -a; . $HOME/.openclaw/.env; set +a

LOG="$TMPDIR/openclaw/watchdog.log"

mkdir -p "$(dirname $LOG)"
log() { echo "$(date '+%H:%M:%S') $*" | tee -a "$LOG"; }
log "Watchdog started (pid=$$)"

# Keep TCP traffic alive to prevent USF hibernation (speed=0 triggers freeze)
( while true; do
    curl -s --max-time 1 http://114.55.130.197:28790/ -o /dev/null 2>/dev/null || true
    sleep 2
  done ) &
TCP_KEEPALIVE_PID=$!


while true; do
  # Check openclaw gateway
  if ! pgrep -f "openclaw gateway" > /dev/null 2>&1; then
    log "OpenClaw not running, restarting..."
    export OPENCLAW_GATEWAY_TOKEN DEEPSEEK_API_KEY SILICONFLOW_API_KEY GLM_API_KEY DASHSCOPE_API_KEY GEMINI_API_KEY
    openclaw gateway --port 28789 --verbose >> "$TMPDIR/openclaw/gateway-stdout.log" 2>&1 &
    sleep 5
  fi

  # Check SSH tunnel
  if ! pgrep -f "ssh.*28789.*114.55.130.197" > /dev/null 2>&1; then
    log "SSH tunnel not running, restarting..."
    ssh \
      -o ServerAliveInterval=5 -o ServerAliveCountMax=2 \
      -o ExitOnForwardFailure=yes -o StrictHostKeyChecking=no \
      -o TCPKeepAlive=yes -o ConnectTimeout=10 \
      -R 28789:127.0.0.1:28789 root@114.55.130.197 -N >> "$LOG" 2>&1 &
  fi

  sleep 8
done
