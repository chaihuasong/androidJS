#!/data/data/com.termux/files/usr/bin/bash
# =============================================================
# ClawBot — Start gateway + SSH tunnel in tmux
# Run this to launch ClawBot after initial setup.
# =============================================================
set -euo pipefail

CLOUD_SERVER="root@114.55.130.197"
GATEWAY_PORT=28789
SESSION_NAME="clawbot"

echo "=== Starting ClawBot ==="

# Acquire wakelock to prevent Android from killing Termux in background
echo "Acquiring wakelock..."
termux-wake-lock 2>/dev/null || true
echo "Wakelock acquired."

# Kill any existing session
tmux kill-session -t "$SESSION_NAME" 2>/dev/null || true

# Create tmux session with the gateway
tmux new-session -d -s "$SESSION_NAME" -n gateway \
  "echo '--- OpenClaw Gateway ---'; openclaw gateway --port $GATEWAY_PORT --verbose; read"

# Wait a moment for gateway to start
sleep 3

# Create a second window for the SSH reverse tunnel
tmux new-window -t "$SESSION_NAME" -n tunnel \
  "echo '--- SSH Reverse Tunnel to $CLOUD_SERVER ---'; echo 'Mapping localhost:$GATEWAY_PORT -> $CLOUD_SERVER:$GATEWAY_PORT'; ssh -o ServerAliveInterval=60 -o ServerAliveCountMax=3 -R $GATEWAY_PORT:127.0.0.1:$GATEWAY_PORT $CLOUD_SERVER -N; echo 'Tunnel disconnected. Press Enter to retry.'; read"

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

# Attach to the session
tmux attach -t "$SESSION_NAME"
