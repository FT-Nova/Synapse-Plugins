# Anthropic Claude Provider

Official SYNAPSE model provider for Anthropic Claude.

## Features

- Claude Opus, Sonnet, Haiku models
- Streaming completions
- Tool calling support
- Vision support
- API key and ACP subscription auth

## Configuration

```yaml
api_key: "sk-ant-..."           # or acp_subscription_id
base_url: "https://api.anthropic.com"
```

## Build

```bash
mvn package
```

Produces `target/anthropic-provider-1.0.0.jar`.

## License

MIT
