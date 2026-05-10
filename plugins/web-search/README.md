# Web Search Plugin

Search the web for current information using multiple search providers.

## Features

- Multiple providers (Google, Bing, DuckDuckGo, SearXNG)
- Time-filtered search
- Webpage content extraction
- Result caching
- Safe search

## Installation

```bash
synapse agent update my-agent --enable-plugin web-search
```

## Configuration

```yaml
plugins:
  web-search:
    provider: duckduckgo
    max_results: 10
    safe_search: true
```

## Tools

- `web_search` - Search the web
- `get_webpage_content` - Extract webpage content

## Documentation

Full documentation: https://ftmahringer.github.io/Synapse/docs/plugins/official/web-search

## License

MIT
