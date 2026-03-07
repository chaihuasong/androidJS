#!/data/data/com.termux/files/usr/bin/bash
# ClawBot 后台存活诊断脚本
# 用法: bash ~/diag.sh
# 退后台后等断连，回来 Ctrl+C，看日志 ~/diag.log

LOG="$HOME/diag.log"
echo "开始诊断，日志写入 $LOG"
echo "退后台等断连后回来按 Ctrl+C 查看日志"
echo "---" | tee "$LOG"

while true; do
  GW=$(pgrep -f openclaw-gateway | wc -l | tr -d ' ')
  SSH=$(pgrep -f 'ssh.*28789' | wc -l | tr -d ' ')
  if ping -c1 -W2 8.8.8.8 &>/dev/null; then
    NET="ok"
  else
    NET="FAIL"
  fi
  LINE="$(date '+%H:%M:%S') gw=$GW ssh=$SSH net=$NET"
  echo "$LINE" | tee -a "$LOG"
  sleep 15
done
