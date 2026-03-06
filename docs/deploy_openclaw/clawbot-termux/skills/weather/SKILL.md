---
name: weather
description: "Get current weather and forecast. Use when asked to: check weather, what's the weather like, weather today/tomorrow/this week, temperature, will it rain, weather forecast, or weather for a specific city."
metadata:
  {
    "openclaw":
      {
        "emoji": "🌤️",
        "requires": { "bins": ["curl"] },
      },
  }
---

# Weather — ClawBot

Get weather using [wttr.in](https://wttr.in) — a public weather service, no API key required.

## Current Weather (concise, one line)

```bash
# Current location (auto-detected by IP)
curl -s "wttr.in/?format=3"

# Specific city
curl -s "wttr.in/Beijing?format=3"
curl -s "wttr.in/Shanghai?format=3"
```

Output example: `Beijing: ⛅️  +18°C`

## Full Weather Report (3-day forecast)

```bash
# Current location
curl -s "wttr.in/?format=v2"

# Specific city
curl -s "wttr.in/Shenzhen?format=v2"
```

## Detailed JSON (for parsing)

```bash
# Get full JSON data for current location
curl -s "wttr.in/?format=j1" | python3 -c "
import json, sys
data = json.load(sys.stdin)
cur = data['current_condition'][0]
area = data['nearest_area'][0]
city = area['areaName'][0]['value']
country = area['country'][0]['value']
temp_c = cur['temp_C']
feels = cur['FeelsLikeC']
desc = cur['weatherDesc'][0]['value']
humidity = cur['humidity']
wind = cur['windspeedKmph']
print(f'Location: {city}, {country}')
print(f'Weather: {desc}')
print(f'Temperature: {temp_c}°C (feels like {feels}°C)')
print(f'Humidity: {humidity}%')
print(f'Wind: {wind} km/h')
"
```

## Today's Hourly Forecast

```bash
curl -s "wttr.in/?format=j1" | python3 -c "
import json, sys
data = json.load(sys.stdin)
today = data['weather'][0]
print('Date:', today['date'])
print('Max:', today['maxtempC'], 'C  Min:', today['mintempC'], 'C')
print()
for h in today['hourly']:
    time = h['time'].zfill(4)
    time_fmt = time[:2] + ':' + time[2:]
    desc = h['weatherDesc'][0]['value']
    temp = h['tempC']
    rain = h['chanceofrain']
    print(f'  {time_fmt}  {temp}°C  {desc}  Rain:{rain}%')
"
```

## Weather for Specific City (Chinese cities)

```bash
# Works with pinyin or English names
curl -s "wttr.in/Guangzhou?format=v2"
curl -s "wttr.in/Chengdu?format=v2"
curl -s "wttr.in/Hangzhou?format=v2"

# Or get current IP-based location weather
curl -s "wttr.in/?format=v2"
```

## Guidelines

- Default to current location (IP-based) if the user doesn't specify a city.
- For a quick answer, use `format=3` (single line); for detailed info, use `format=v2` or JSON.
- If the user asks about rain, extract `chanceofrain` from the hourly data.
- City names: use English pinyin for Chinese cities (Beijing, Shanghai, Shenzhen, etc.).
- wttr.in is a free public service — no API key, no rate limits for normal use.
- If `curl` returns an error or empty response, the service may be temporarily unavailable; suggest trying again.
