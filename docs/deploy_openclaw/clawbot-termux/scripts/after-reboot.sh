#!/bin/bash
# ClawBot 手机重启后恢复脚本
# 在 Mac/PC 上执行（需要 adb 连接手机，手机已开机）
# 用法: bash after-reboot.sh

set -e

echo "=== ClawBot 重启恢复 ==="
echo ""

# ── 切换 adb 为 root 模式 ─────────────────────────────────────────────────────
echo "切换 adb root 模式..."
adb root
sleep 2
echo ""

# ── 1. Phantom Process Killer ────────────────────────────────────────────────
echo "[1/3] 禁用 Phantom Process Killer..."
adb shell device_config put activity_manager max_phantom_processes 2147483647
echo "      完成"
echo ""

# ── 2. USF Hiber bind mount ──────────────────────────────────────────────────
echo "[2/3] 重新应用 USF Hiber bind mount..."

PATCHED="/data/local/tmp/hiber_minimal.json"
SYSTEM_CONFIG="/system_ext/etc/vconfig/tranusf/config/hiber/hiber.json"

# 如果补丁文件不存在，自动从系统拉取并生成
FILE_EXISTS=$(adb shell "test -f $PATCHED && echo yes || echo no" | tr -d '\r')
if [ "$FILE_EXISTS" != "yes" ]; then
  echo "      $PATCHED 不存在，正在自动生成..."

  TMP_LOCAL=$(mktemp /tmp/hiber_XXXXXX.json)

  # 从手机拉取系统配置
  adb shell "cat $SYSTEM_CONFIG" > "$TMP_LOCAL"

  # 用 python3 将 Termux 包名注入 whitePackages 数组
  python3 - "$TMP_LOCAL" <<'PYEOF'
import json, sys

path = sys.argv[1]
with open(path, 'r') as f:
    data = json.load(f)

pkgs = ["com.termux", "com.termux.api", "com.termux.gui"]

def inject(obj):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k == "whitePackages" and isinstance(v, list):
                for p in pkgs:
                    if p not in v:
                        v.append(p)
                        print(f"  + {p}")
            else:
                inject(v)
    elif isinstance(obj, list):
        for item in obj:
            inject(item)

inject(data)

with open(path, 'w') as f:
    json.dump(data, f, indent=2, ensure_ascii=False)
PYEOF

  adb push "$TMP_LOCAL" /data/local/tmp/hiber_minimal.json
  rm "$TMP_LOCAL"
  echo "      hiber_minimal.json 已生成并推送"
fi

# 应用 bind mount
adb shell "mount --bind $PATCHED $SYSTEM_CONFIG"
adb shell "stop hiber && sleep 1 && start hiber && sleep 3"
adb shell "kill -9 \$(pgrep -f 'com.transsion.usf\$' | head -1) 2>/dev/null || true"
echo "      完成（等待 USF 重启...）"
sleep 3

# 验证
RESULT=$(adb shell "dumpsys activity service com.transsion.usf/.UsfMainService -ability -hiber 2>/dev/null" | tr -d '\r')
if echo "$RESULT" | grep -q "isEnabled=false"; then
  echo "      验证通过：Termux 已从冻结列表移除"
else
  echo "      [警告] 无法验证，请手动检查："
  echo "      adb shell \"dumpsys activity service com.transsion.usf/.UsfMainService -ability -hiber\""
fi
echo ""

# ── 3. 提示启动 ClawBot ──────────────────────────────────────────────────────
echo "[3/3] 现在请在 Termux 前台执行："
echo ""
echo "      bash ~/clawbot-launch.sh"
echo ""
echo "=== 完成 ==="
