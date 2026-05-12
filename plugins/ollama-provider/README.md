# Ollama Provider

Official SYNAPSE model provider for local Ollama inference.

## Features

- Self-hosted — zero external API calls
- Streaming completions
- Dynamic model list from Ollama server
- Embeddings support

## Configuration

```yaml
base_url: "http://localhost:11434"
default_model: "llama3.2"
```

## Build

```bash
mvn package
```

Produces `target/ollama-provider-1.0.0.jar`.

## License

MIT
