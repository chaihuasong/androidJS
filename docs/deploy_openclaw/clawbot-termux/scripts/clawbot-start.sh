#!/data/data/com.termux/files/usr/bin/bash
# =============================================================
# ClawBot — Start gateway + SSH tunnel in tmux
# Run this to launch ClawBot after initial setup.
# =============================================================
set -euo pipefail

CLOUD_SERVER="root@114.55.130.197"
GATEWAY_PORT=28789
SESSION_NAME="clawbot"
OPENCLAW=/data/data/com.termux/files/usr/bin/openclaw
NODE=/data/data/com.termux/files/usr/bin/node
SSH=/data/data/com.termux/files/usr/bin/ssh
export HOME=/data/data/com.termux/files/home
export TMPDIR=/data/data/com.termux/files/usr/tmp
export PATH=/data/data/com.termux/files/usr/bin:/data/data/com.termux/files/usr/bin/applets:$PATH

echo "=== Starting ClawBot ==="

# Acquire wakelock to prevent Android from killing Termux in background
echo "Acquiring wakelock..."
termux-wake-lock 2>/dev/null || true
echo "Wakelock acquired."


# Kill any existing session
tmux kill-session -t "$SESSION_NAME" 2>/dev/null || true

# Create tmux session with the gateway
tmux new-session -d -s "$SESSION_NAME" -n gateway \
  "export HOME=/data/data/com.termux/files/home; export TMPDIR=/data/data/com.termux/files/usr/tmp; export PATH=/data/data/com.termux/files/usr/bin:/data/data/com.termux/files/usr/bin/applets:\$PATH; echo '--- OpenClaw Gateway ---'; $NODE $OPENCLAW gateway --port $GATEWAY_PORT --verbose; read"

# Wait a moment for gateway to start
sleep 5

# Create a second window for the SSH reverse tunnel (auto-reconnect loop)
tmux new-window -t "$SESSION_NAME" -n tunnel \
  "export HOME=/data/data/com.termux/files/home; export PATH=/data/data/com.termux/files/usr/bin:\$PATH; echo '--- SSH Reverse Tunnel to $CLOUD_SERVER (auto-reconnect) ---'; while true; do echo \"[\$(date '+%H:%M:%S')] Killing stale remote port...\"; $SSH -o ConnectTimeout=10 $CLOUD_SERVER \"fuser -k $GATEWAY_PORT/tcp 2>/dev/null; true\"; echo \"[\$(date '+%H:%M:%S')] Connecting tunnel...\"; $SSH -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -o TCPKeepAlive=yes -o ExitOnForwardFailure=yes -o ConnectTimeout=10 -R $GATEWAY_PORT:127.0.0.1:$GATEWAY_PORT $CLOUD_SERVER -N; echo \"[\$(date '+%H:%M:%S')] Tunnel disconnected, reconnecting in 5s...\"; sleep 5; done"

echo ""
echo "ClawBot is running in tmux session '$SESSION_NAME'"
echo ""
echo "  Gateway:  window 0 (gateway)"
echo "  Tunnel:   window 1 (tunnel)"
echo ""
echo "  Attach:   tmux attach -t $SESSION_NAME"
echo "  Detach:   Ctrl+B, D"
echo "  Switch:   Ctrl+B, N (next window)"
echo ""
echo "  Local UI: http://localhost:$GATEWAY_PORT"
echo "  Remote:   http://114.55.130.197:28790"
echo ""
echo "  查看日志: tmux attach -t $SESSION_NAME"
