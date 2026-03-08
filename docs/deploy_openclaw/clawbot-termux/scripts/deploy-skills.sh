#!/bin/bash
# 部署 Skills 到手机
# 在 Mac/PC 上执行（adb 连接手机）
# 用法: bash deploy-skills.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SKILLS_DIR="$SCRIPT_DIR/../skills"
DEST_SKILLS="/data/data/com.termux/files/home/.openclaw/workspace/skills"
DEST_HOME="/data/data/com.termux/files/home"

echo "=== 部署 Skills 到手机 ==="
echo ""

push_skill() {
  local name="$1"
  local src="$SKILLS_DIR/$name"

  echo "[$name] 推送 SKILL.md..."
  adb shell mkdir -p "$DEST_SKILLS/$name" 2>/dev/null || \
    adb shell "run-as com.termux mkdir -p $DEST_SKILLS/$name"
  adb push "$src/SKILL.md" "$DEST_SKILLS/$name/SKILL.md"

  if [ -f "$src/$name.py" ]; then
    echo "[$name] 推送 $name.py..."
    adb push "$src/$name.py" "$DEST_HOME/$name.py"
    adb shell chmod 755 "$DEST_HOME/$name.py"
  fi
}

# ── Skills ───────────────────────────────────────────────────────────────────
push_skill "split-open"
push_skill "ui-control"
push_skill "screenshot"
push_skill "adb-shell"
push_skill "vision-tap"

echo ""
echo "完成！已部署："
echo "  ~/.openclaw/workspace/skills/split-open/SKILL.md  +  ~/split-open.py"
echo "  ~/.openclaw/workspace/skills/ui-control/SKILL.md  +  ~/ui-control.py"
echo "  ~/.openclaw/workspace/skills/screenshot/SKILL.md"
echo "  ~/.openclaw/workspace/skills/adb-shell/SKILL.md"
echo "  ~/.openclaw/workspace/skills/vision-tap/SKILL.md  +  ~/vision-tap.py"
echo ""
echo "重启 ClawBot 后生效（skills 支持热加载，也可直接使用）。"
