---
name: adb-shell
description: "Execute any adb shell command on the phone. Use for: opening apps (am start), confirming dialogs, pressing hardware buttons, checking running processes (ps/dumpsys), reading system properties (getprop), managing packages (pm), sending intents (am broadcast), checking network state, or any system-level operation not covered by other skills."
metadata:
  {
    "openclaw":
      {
        "emoji": "🔧",
        "requires": { "bins": ["adb"] },
      },
  }
---

# ADB Shell — ClawBot

执行 Android shell 命令。adb 自动连接本机设备（`emulator-5554`），**无需任何 `-H`、`-P`、`-s` 参数**。

命令格式（固定，不要修改）：
```
/data/data/com.termux/files/usr/bin/adb shell <cmd>
```

---

## 常用场景

### 打开 App

```bash
# 通过包名/Activity 启动
/data/data/com.termux/files/usr/bin/adb shell am start -n com.tencent.mm/.ui.LauncherUI          # 微信
/data/data/com.termux/files/usr/bin/adb shell am start -n com.eg.android.AlipayGphone/.AlipayLogin  # 支付宝
/data/data/com.termux/files/usr/bin/adb shell am start -n com.taobao.taobao/.main.MainActivity   # 淘宝
/data/data/com.termux/files/usr/bin/adb shell am start -a android.settings.SETTINGS              # 系统设置
/data/data/com.termux/files/usr/bin/adb shell am start -a android.intent.action.VIEW -d "https://example.com"  # 浏览器打开URL

# 只知道包名时（跳转默认入口）
/data/data/com.termux/files/usr/bin/adb shell monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1
```

### 确认/关闭弹窗

```bash
# 回车确认（等价于 ui-control key enter）
/data/data/com.termux/files/usr/bin/adb shell input keyevent 66

# 点击坐标（当 ui-control tap-re 找不到元素时）
/data/data/com.termux/files/usr/bin/adb shell input tap 540 1200

# 返回键关闭弹窗
/data/data/com.termux/files/usr/bin/adb shell input keyevent 4
```

### 查看当前 App / Activity

```bash
# 当前前台 Activity（确认在哪个页面）
/data/data/com.termux/files/usr/bin/adb shell dumpsys activity activities | grep -E 'mResumedActivity|topActivity'

# 当前前台包名（简洁）
/data/data/com.termux/files/usr/bin/adb shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'
```

### 包管理

```bash
# 查找已安装 App 的包名
/data/data/com.termux/files/usr/bin/adb shell pm list packages | grep wechat
/data/data/com.termux/files/usr/bin/adb shell pm list packages -3          # 只列第三方 App

# 查询 App 主 Activity（用于 am start -n）
/data/data/com.termux/files/usr/bin/adb shell cmd package resolve-activity --brief com.tencent.mm
```

### 系统信息

```bash
# 设备属性
/data/data/com.termux/files/usr/bin/adb shell getprop ro.product.model
/data/data/com.termux/files/usr/bin/adb shell getprop ro.build.version.release

# 屏幕分辨率
/data/data/com.termux/files/usr/bin/adb shell wm size

# 运行中的进程
/data/data/com.termux/files/usr/bin/adb shell ps -A | grep tencent
```

### 发送 Intent / 广播

```bash
# 打开通知栏
/data/data/com.termux/files/usr/bin/adb shell cmd statusbar expand-notifications

# 收起通知栏
/data/data/com.termux/files/usr/bin/adb shell cmd statusbar collapse

# 亮屏
/data/data/com.termux/files/usr/bin/adb shell input keyevent 224

# 熄屏
/data/data/com.termux/files/usr/bin/adb shell input keyevent 223
```

---

## Guidelines

- **严禁**运行 `adb devices` 或任何连通性检查命令；ADB TCP loopback 始终可用，直接执行目标命令
- 若某条 adb 命令超时，**直接换用 su 等价命令**，不要报告"ADB 连不上"
- 优先用 **ui-control** 做 UI 交互（dump/tap/type）；adb-shell 用于 ui-control 覆盖不到的场景
- 打开 App 时：先尝试 `am start -n <包名>/<Activity>`，不知道 Activity 时用 `monkey -p <包名>`
- 点击坐标前先用 `wm size` 确认屏幕分辨率，避免坐标超出范围
- 执行完打开 App 的命令后，等待 1-2 秒再用 ui-control dump-clickable 确认页面已加载
