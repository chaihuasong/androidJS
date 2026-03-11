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

## 注意

- 每天免费 100 次查询
- 支持中英文搜索
- 搜索结果为全网搜索
- 凭据存储于 `~/.openclaw/.env`：`GOOGLE_SEARCH_API_KEY` 和 `GOOGLE_SEARCH_CX`
