# Code Execution Plugin

Execute code in sandboxed environments.

## Features

- Multi-language support (Python, JavaScript, Java, Bash, Ruby, Go)
- Sandboxed execution
- Resource limits
- Network isolation
- Persistent environments (optional)

## Installation

```bash
synapse agent update my-agent --enable-plugin code-execution
```

## Configuration

```yaml
plugins:
  code-execution:
    timeout_seconds: 30
    max_memory_mb: 512
    network_access: false
```

## Tools

- `execute_code` - Execute code in sandbox

## Documentation

Full documentation: https://ftmahringer.github.io/Synapse/docs/plugins/official/code-execution

## License

MIT
