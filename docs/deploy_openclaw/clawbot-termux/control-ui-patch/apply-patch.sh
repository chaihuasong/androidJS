#!/bin/bash
# 将 Control UI patch 推送到手机
# 在 Mac/PC 上执行（需要 adb 连接手机）
# 用法: bash apply-patch.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PATCH_JS="$SCRIPT_DIR/patch.js"
DEST="/data/data/com.termux/files/usr/lib/node_modules/openclaw/dist/control-ui"

echo "推送 patch.js 到手机..."
base64 -i "$PATCH_JS" | adb shell "run-as com.termux sh -c 'base64 -d > ${DEST}/patch.js'"

echo "推送 index.html（注入 patch.js 引用）..."
# 读取当前 index.html，插入 patch.js script 标签
adb shell "run-as com.termux sh -c '
  DEST=${DEST}
  # 如果还没注入过，则在 <script type=\"module\" 前插入
  if ! grep -q patch.js \$DEST/index.html; then
    sed -i \"s|<script type=\\\"module\\\"|<script src=\\\"./patch.js?v=13\\\"></script>\\n    <script type=\\\"module\\\"|\" \$DEST/index.html
    echo patched
  else
    # 更新版本号
    sed -i \"s|patch.js?v=[0-9]*|patch.js?v=13|\" \$DEST/index.html
    echo updated
  fi
'"

echo "完成！在浏览器中刷新页面（F5）后生效。"
echo "Console 里看到 [clawbot-patch] v11 loaded 即表示成功。"
