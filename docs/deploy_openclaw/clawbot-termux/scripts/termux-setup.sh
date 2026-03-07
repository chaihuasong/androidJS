#!/data/data/com.termux/files/usr/bin/bash
# =============================================================
# ClawBot — Termux initial setup script
# Run this ONCE inside Termux on the Android phone.
# Prerequisites: Termux + Termux:API installed from F-Droid
#                (both must come from the same source).
# =============================================================
set -euo pipefail

echo "=== ClawBot Termux Setup ==="
echo ""

# ---- 1. Update packages ----
echo "[1/6] Updating packages..."
pkg update -y && pkg upgrade -y

# ---- 2. Install dependencies ----
echo "[2/6] Installing Node.js, Python, termux-api, openssh, tmux..."
pkg install nodejs-lts python termux-api openssh tmux -y

echo "  Installing Python dependencies (Pillow for screenshot compression)..."
pip install Pillow --quiet

# ---- 3. Environment variables for Node.js ----
echo "[3/6] Configuring environment variables..."
grep -q 'TMPDIR' ~/.bashrc 2>/dev/null || {
  cat >> ~/.bashrc <<'ENVBLOCK'

# ClawBot / OpenClaw environment
export TMPDIR="$PREFIX/tmp"
export TMP="$TMPDIR"
export TEMP="$TMPDIR"
ENVBLOCK
}
source ~/.bashrc
mkdir -p "$PREFIX/tmp/openclaw"

# ---- 4. Install OpenClaw ----
echo "[4/6] Installing OpenClaw..."
npm install -g openclaw@latest
# Fix shebang for Termux (uses /usr/bin/env which doesn't exist in Termux)
termux-fix-shebang "$PREFIX/lib/node_modules/openclaw/openclaw.mjs" 2>/dev/null || true

# ---- 5. Deploy configuration files ----
echo "[5/6] Deploying configuration files..."
mkdir -p ~/.openclaw/workspace/skills/phone-control

# Copy openclaw.json (skip if already exists to avoid overwriting user edits)
if [ ! -f ~/.openclaw/openclaw.json ]; then
  cp "$(dirname "$0")/../openclaw.json" ~/.openclaw/openclaw.json
  echo "  -> Deployed ~/.openclaw/openclaw.json"
else
  echo "  -> ~/.openclaw/openclaw.json already exists, skipping"
fi

# Always update SKILL.md
cp "$(dirname "$0")/../skills/phone-control/SKILL.md" \
   ~/.openclaw/workspace/skills/phone-control/SKILL.md
echo "  -> Deployed phone-control SKILL.md"

# Create .env if it doesn't exist
if [ ! -f ~/.openclaw/.env ]; then
  GENERATED_TOKEN=$(openssl rand -hex 32 2>/dev/null || head -c 64 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 64)
  cat > ~/.openclaw/.env <<EOF
# ClawBot gateway authentication token
OPENCLAW_GATEWAY_TOKEN=${GENERATED_TOKEN}
EOF
  echo "  -> Generated ~/.openclaw/.env with random gateway token"
  echo "  -> Token: ${GENERATED_TOKEN}"
  echo "  ** Save this token — you will need it to access the Web UI **"
else
  echo "  -> ~/.openclaw/.env already exists, skipping"
fi

# ---- 6. Verify termux-api ----
echo "[6/6] Verifying termux-api commands..."
echo ""
echo "Testing flashlight..."
termux-torch on && sleep 1 && termux-torch off && echo "  OK: flashlight works" || echo "  WARN: flashlight failed (check Termux:API permissions)"

echo ""
echo "Testing sensor list..."
termux-sensor -l 2>/dev/null | head -5 && echo "  OK: sensors accessible" || echo "  WARN: sensors failed (check permissions)"

echo ""
echo "Testing camera info..."
termux-camera-info 2>/dev/null | head -5 && echo "  OK: camera accessible" || echo "  WARN: camera failed (check permissions)"

echo ""
echo "============================================"
echo "  Setup complete!"
echo ""
echo "  Next steps:"
echo "    1. Run: openclaw onboard"
echo "    2. Run: bash $(dirname "$0")/clawbot-start.sh"
echo "============================================"
