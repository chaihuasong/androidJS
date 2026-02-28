# ClawBot 运行架构与启动说明

## 一、启动流程

```
用户在 Termux 执行: bash ~/clawbot-launch.sh
    │
    ├─ 1. 加载环境变量 (~/.openclaw/.env)
    │      OPENCLAW_GATEWAY_TOKEN / DEEPSEEK_API_KEY / SILICONFLOW_API_KEY
    │
    ├─ 2. termux-wake-lock (防止 Android 杀后台)
    │
    ├─ 3. 清理旧进程 (pkill openclaw / ssh / tmux)
    │
    ├─ 4. tmux session "clawbot"
    │      │
    │      ├─ 窗口 0 [gw]: openclaw gateway --port 28789 --verbose
    │      │                 (等待 12 秒启动完成)
    │      │
    │      └─ 窗口 1 [tun]: bash ~/tunnel-loop.sh
    │                        (SSH 反向隧道，断线自动重连)
    │
    └─ 5. 输出 "ClawBot started"
```

## 二、进程树

```
tmux ─── session: clawbot
  │
  ├─ [窗口 0: gw]
  │    └─ bash
  │         ├─ openclaw                      ← Node.js 主进程
  │         │    └─ openclaw-gateway          ← Gateway 子进程, 监听 :28789
  │         └─ tee                           ← 日志写入 gateway-stdout.log
  │
  └─ [窗口 1: tun]
       └─ tunnel-loop.sh
            └─ ssh                           ← SSH 反向隧道
                 -R 28789:127.0.0.1:28789 root@114.55.130.197
```

## 三、网络链路

```
用户浏览器
    ↓ HTTP/WebSocket
云服务器 114.55.130.197
    ├─ Nginx (:28790)              ← 反向代理
    │    ├─ /           → proxy_pass 127.0.0.1:28789 (隧道)
    │    └─ /photos/    → /var/www/clawbot-photos/ (静态图片)
    ↓
SSH 反向隧道 (服务器 127.0.0.1:28789 → 手机 127.0.0.1:28789)
    ↓
手机 OpenClaw Gateway (:28789)
    ├─ Control UI (Web 聊天界面)
    ├─ AI Agent (调用 DeepSeek Chat API)
    │    └─ exec/bash 工具 → Termux shell → termux-api 命令
    └─ Skills 加载 (watch 模式，自动热加载)
```

## 四、手机文件结构

```
~/                                    ← /data/data/com.termux/files/home/
├── clawbot-launch.sh                 ← 一键启动脚本
├── tunnel-loop.sh                    ← SSH 隧道自动重连
│
└── .openclaw/
    ├── .env                          ← API Keys + Gateway Token
    ├── openclaw.json                 ← Gateway 配置 (端口/模型/认证)
    ├── agents/
    │   ├── default/                  ← ClawBot 默认 Agent
    │   │   ├── agent/                ← Agent 配置
    │   │   └── sessions/             ← 对话会话记录
    │   └── main/
    ├── workspace/
    │   └── skills/                   ← 技能目录 (watch 热加载)
    │       ├── phone-control/SKILL.md   ← 16 项手机控制
    │       └── photo-share/SKILL.md     ← 拍照上传显示
    ├── canvas/
    └── cron/

OpenClaw 程序位置:
  /usr/bin/openclaw → ../lib/node_modules/openclaw/openclaw.mjs (npm 全局安装)
  版本: 2026.2.9
```

## 五、云服务器文件

| 文件 | 作用 |
|------|------|
| `/etc/nginx/conf.d/clawbot.conf` | Nginx 反向代理 + 静态图片服务 |
| `/var/www/clawbot-photos/` | 手机上传的图片存储目录 |

## 六、关键机制

| 机制 | 说明 |
|------|------|
| **tmux** | 保持进程在后台运行，两个窗口分别管理 Gateway 和隧道 |
| **termux-wake-lock** | 防止 Android 系统杀死 Termux |
| **tunnel-loop.sh** | `while true` 循环，SSH 断线后 5 秒自动重连 |
| **skills watch** | `openclaw.json` 配置了 `watch: true`，新增/修改 SKILL.md 自动热加载 |
| **日志** | stdout → `tee` 写到 `$PREFIX/tmp/openclaw/gateway-stdout.log`；另有 `openclaw.log` |

## 七、常用运维命令

```bash
# 启动
bash ~/clawbot-launch.sh

# 查看 tmux 会话
tmux attach -t clawbot

# 切换窗口
Ctrl+B, N    # 下一个窗口
Ctrl+B, 0    # 切到 Gateway 窗口
Ctrl+B, 1    # 切到隧道窗口

# 脱离（不关闭）
Ctrl+B, D

# 查看日志
tail -f $PREFIX/tmp/openclaw/gateway-stdout.log
cat $PREFIX/tmp/openclaw/openclaw.log | tail -30

# 停止
tmux kill-session -t clawbot
pkill -f openclaw
pkill -f "ssh.*28789"

# 检查进程
ps -ef | grep -E 'tmux|openclaw|ssh' | grep -v grep

# 检查隧道连通性（在云服务器上执行）
curl -s http://127.0.0.1:28789/
```

## 八、OpenClaw UI 补丁

由于 OpenClaw Control UI 默认不支持在聊天中内联显示图片，需要手动打补丁：

**文件位置：** `/usr/lib/node_modules/openclaw/dist/control-ui/assets/`

### 1. JS 补丁 — 允许 img 标签和 src 属性

在 `index-BeKTXH1m.js` 中：

```
# ALLOWED_TAGS 加入 "img"
Mo=[..."i","img","li"...]

# ALLOWED_ATTR 加入 "alt" 和 "src"
Io=["alt","class","href","rel","src","target","title","start"]
```

### 2. CSS 补丁 — 限制图片显示尺寸

在 `index-DWhx-9JL.css` 末尾追加：

```css
.chat-bubble img{max-width:100%;max-height:400px;border-radius:8px;object-fit:contain}
```

**注意：** `npm update openclaw` 会覆盖这些补丁，更新后需重新打补丁。
