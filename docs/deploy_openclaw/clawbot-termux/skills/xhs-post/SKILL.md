---
name: xhs-post
description: "Publish a note/article to Xiaohongshu (小红书). Use when asked to: post to xiaohongshu, publish a note on xhs, create a xiaohongshu article, share content to 小红书, or write a post on 红书. Requires Xiaohongshu app installed and logged in."
metadata:
  {
    "openclaw":
      {
        "emoji": "📕",
        "requires": { "bins": ["uiautomator", "am"] },
      },
  }
---

# 小红书发布 — ClawBot

自动控制小红书 App 发布图文笔记。需要手机上已安装小红书并登录账号。

## 发布笔记

```bash
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/xhs-post.py "标题" "正文内容"
```

示例：
```bash
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/xhs-post.py "今天的好心情" "阳光明媚，出去走走，生活就是这么简单美好 ☀️"
```

## 流程说明

脚本自动执行：
1. 启动小红书 App
2. 点击发布（+）按钮
3. 选择「写笔记」
4. 输入标题和正文（通过剪贴板，支持中文）
5. 点击发布
6. 截图保存到 `/sdcard/xhs-preview.png`

## 使用前提

- 小红书 App 已安装：`pm list packages | grep xingin`
- 已登录账号
- 脚本已部署：`~/xhs-post.py`

## 注意事项

- 发布后请提示用户检查手机确认是否成功
- 如果 App 版本更新导致 UI 变化，可能需要查看截图手动确认
- 正文支持 emoji，中文通过剪贴板输入，无乱码问题
- 如需附图，暂时先让用户在小红书中手动添加图片后再点发布
- **必须**将脚本的完整输出（每一行）原样展示给用户，包括步骤进度和最终结果
