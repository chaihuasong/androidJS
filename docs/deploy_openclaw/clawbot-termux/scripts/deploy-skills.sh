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
  adb shell "run-as com.termux mkdir -p $DEST_SKILLS/$name"
  base64 -i "$src/SKILL.md" | adb shell "run-as com.termux sh -c 'base64 -d > $DEST_SKILLS/$name/SKILL.md'"

  if [ -f "$src/$name.py" ]; then
    echo "[$name] 推送 $name.py..."
    base64 -i "$src/$name.py" | adb shell "run-as com.termux sh -c 'base64 -d > $DEST_HOME/$name.py && chmod 755 $DEST_HOME/$name.py'"
  fi
}

# ── Skills ───────────────────────────────────────────────────────────────────
push_skill "xhs-post"
push_skill "split-open"
push_skill "ui-control"
push_skill "screenshot"

echo ""
echo "完成！已部署："
echo "  ~/.openclaw/workspace/skills/xhs-post/SKILL.md  +  ~/xhs-post.py"
echo "  ~/.openclaw/workspace/skills/split-open/SKILL.md  +  ~/split-open.py"
echo "  ~/.openclaw/workspace/skills/ui-control/SKILL.md  +  ~/ui-control.py"
echo ""
echo "重启 ClawBot 后生效（skills 支持热加载，也可直接使用）。"
