---
name: ui-control
description: "Control the phone's UI by analyzing the screen and clicking/typing/scrolling. Use when asked to: click a button, tap an element, type text into an app, scroll the screen, find and interact with any UI element, analyze what's on screen, or perform any touch interaction on the phone. This skill uses uiautomator to inspect the current screen layout and execute precise interactions."
metadata:
  {
    "openclaw":
      {
        "emoji": "👆",
        "requires": { "bins": ["uiautomator", "input", "screencap"] },
      },
  }
---

# UI 控制 — ClawBot

通过 uiautomator 分析手机当前屏幕，自动找到目标元素并执行点击、输入、滑动等操作。

## 查看当前屏幕内容

```bash
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/ui-control.py dump
```

输出当前屏幕所有可交互元素（文字、按钮、输入框）。

## 点击元素

```bash
# 按文字点击
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/ui-control.py tap "按钮文字"

# 按 resource-id 关键词点击
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/ui-control.py tap-id "resource_id_keyword"
```

## 输入文字

```bash
# 先 tap 聚焦输入框，再 type 输入
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/ui-control.py tap "输入框占位文字"
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/ui-control.py type "要输入的内容"
```

## 滑动

```bash
# 向上滑动（翻页）
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/ui-control.py swipe up
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/ui-control.py swipe down
```

## 按系统键

```bash
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/ui-control.py key back    # 返回
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/ui-control.py key home    # Home
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/ui-control.py key recent  # 最近任务
```

## 截屏（屏幕截图，不是相机拍照）

```bash
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/ui-control.py screenshot
```

> 注意：这是截取手机屏幕当前画面，需要屏幕处于亮屏解锁状态。与相机拍照（termux-camera-photo）完全不同。

## Guidelines

- **操作前必须先 dump**，了解当前屏幕有哪些元素，再决定点什么
- dump 输出会列出所有元素的 text、content-desc、resource-id 和坐标
- 如果 tap 找不到目标文字，尝试 tap-id 用 resource-id 关键词匹配
- 输入中文必须用 type 命令（通过剪贴板输入，无乱码）
- 操作后等待 1-2 秒再 dump 确认结果
- **必须**将脚本完整输出原样展示给用户
