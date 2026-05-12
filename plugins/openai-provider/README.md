# OpenAI Provider

Official SYNAPSE model provider for OpenAI GPT.

## Features

- GPT-4o, GPT-4o-mini, GPT-4 Turbo
- Streaming completions
- Tool calling support
- Organization scoping

## Configuration

```yaml
api_key: "sk-..."
organization_id: ""  # optional
base_url: "https://api.openai.com"
```

## Build

```bash
mvn package
```

Produces `target/openai-provider-1.0.0.jar`.

## License

MIT
