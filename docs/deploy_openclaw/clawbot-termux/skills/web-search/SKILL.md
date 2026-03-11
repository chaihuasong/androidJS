# web-search 技能

使用 Google Custom Search API 执行网络搜索，获取真实网页结果。

## 何时使用

需要搜索互联网信息时，优先使用本技能，而不是直接用浏览器 UI 操作。

## 使用方法

```bash
# 基本搜索（返回10条）
GOOGLE_SEARCH_API_KEY=$(grep GOOGLE_SEARCH_API_KEY ~/.openclaw/.env | cut -d= -f2) \
GOOGLE_SEARCH_CX=$(grep GOOGLE_SEARCH_CX ~/.openclaw/.env | cut -d= -f2) \
bash ~/.openclaw/workspace/skills/web-search/web-search.sh "搜索关键词"

# 指定返回条数
GOOGLE_SEARCH_API_KEY=$(grep GOOGLE_SEARCH_API_KEY ~/.openclaw/.env | cut -d= -f2) \
GOOGLE_SEARCH_CX=$(grep GOOGLE_SEARCH_CX ~/.openclaw/.env | cut -d= -f2) \
bash ~/.openclaw/workspace/skills/web-search/web-search.sh "搜索关键词" 5
```

## 返回格式

每条结果包含：
- 标题
- 链接（URL）
- 摘要

## 读取网页详情（获得链接后直接用 curl，无需打开浏览器）

拿到 URL 后，用 curl + python 直接抓取正文，比用 ui-control 打开浏览器快 10 倍：

```bash
CURL=/data/data/com.termux/files/usr/bin/curl
PYTHON=/data/data/com.termux/files/usr/bin/python3

$CURL -s -L --max-time 15 -A "Mozilla/5.0" "目标URL" | \
$PYTHON -c "
import sys, re
html = sys.stdin.read()
text = re.sub(r'<(script|style)[^>]*>.*?</(script|style)>', '', html, flags=re.DOTALL|re.I)
text = re.sub(r'<[^>]+>', ' ', text)
text = re.sub(r'[ \t]+', ' ', text)
text = re.sub(r'\n{3,}', '\n\n', text)
print(text[:8000])
"
```

批量读取多个链接：
```bash
for URL in "链接1" "链接2" "链接3"; do
  echo "=== $URL ==="
  $CURL -s -L --max-time 15 -A "Mozilla/5.0" "$URL" | \
  $PYTHON -c "
import sys, re
html = sys.stdin.read()
text = re.sub(r'<(script|style)[^>]*>.*?</(script|style)>', '', html, flags=re.DOTALL|re.I)
text = re.sub(r'<[^>]+>', ' ', text)
text = re.sub(r'[ \t]+', ' ', text)
text = re.sub(r'\n{3,}', '\n\n', text)
print(text[:5000])
"
  echo ""
done
```

> ⚠️ **禁止为了读取网页内容而打开浏览器**。curl 直接抓取即可，只有需要登录/交互的页面才用 ui-control。

## 注意

- 每天免费 100 次查询
- 支持中英文搜索
- 搜索结果为全网搜索
- 凭据存储于 `~/.openclaw/.env`：`GOOGLE_SEARCH_API_KEY` 和 `GOOGLE_SEARCH_CX`
