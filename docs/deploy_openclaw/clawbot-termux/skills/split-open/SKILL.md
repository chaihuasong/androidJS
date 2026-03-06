---
name: split-open
description: "Open any app in the bottom half of split-screen mode. Use when asked to: open an app in split screen, show an app at the bottom while keeping current view, launch split screen with a specific app, open two apps side by side, or view an app in the bottom half."
metadata:
  {
    "openclaw":
      {
        "emoji": "⬇️",
        "requires": { "bins": ["uiautomator", "am"] },
      },
  }
---

# 分屏打开应用 — ClawBot

在底部分屏打开任意应用，无需手动操作。利用 TECNO 手机内置的分屏启动器实现。

## 打开指定应用到底部分屏

```bash
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/split-open.py "应用名称"
```

示例：
```bash
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/split-open.py "小红书"
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/split-open.py "微信"
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/split-open.py "Chrome"
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/split-open.py "抖音"
```

## 流程

1. 触发 TECNO 分屏启动器
2. 在桌面上找到目标应用图标（支持中英文模糊匹配）
3. 点击 → 应用在底部分屏打开

## Guidelines

- 应用名称支持中文和英文，部分匹配即可（如 "红书" 可以找到 "小红书"）
- 如果第一屏没找到，脚本会自动向下滑动一页再搜索
- 如果找不到，会列出当前可见的所有应用名供参考
- 适用于 TECNO 手机（使用 SplitScreenDisplayLauncher）
- **必须**将脚本的完整输出（每一行）原样展示给用户，包括步骤进度和最终结果
