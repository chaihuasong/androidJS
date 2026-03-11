#!/data/data/com.termux/files/usr/bin/bash
# web-search skill — Google Custom Search API
# Usage: web-search.sh <query> [num_results]

QUERY="$1"
NUM="${2:-10}"
API_KEY="${GOOGLE_SEARCH_API_KEY}"
CX="${GOOGLE_SEARCH_CX}"

if [ -z "$QUERY" ]; then
  echo "Usage: web-search.sh <query> [num_results]" >&2
  exit 1
fi

CURL=/data/data/com.termux/files/usr/bin/curl

result=$($CURL -s --get \
  "https://www.googleapis.com/customsearch/v1" \
  --data-urlencode "q=$QUERY" \
  -d "key=$API_KEY&cx=$CX&num=$NUM")

echo "$result" | /data/data/com.termux/files/usr/bin/python3 -c "
import sys, json

data = json.load(sys.stdin)

if 'error' in data:
    print('搜索失败:', data['error']['message'])
    sys.exit(1)

items = data.get('items', [])
total = data.get('searchInformation', {}).get('totalResults', '0')
print(f'共找到约 {total} 条结果，返回前 {len(items)} 条：\n')

for i, item in enumerate(items, 1):
    print(f'{i}. {item[\"title\"]}')
    print(f'   链接: {item[\"link\"]}')
    print(f'   摘要: {item.get(\"snippet\", \"\").replace(chr(10), \" \")}')
    print()
"
