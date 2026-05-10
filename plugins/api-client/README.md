# API Client Plugin

HTTP/REST API integration for SYNAPSE agents.

## Features

- All HTTP methods (GET, POST, PUT, PATCH, DELETE)
- Authentication (API Key, Bearer, Basic Auth, OAuth)
- Request/response transformation
- Error handling & retries
- Rate limiting

## Installation

```bash
synapse agent update my-agent --enable-plugin api-client
```

## Configuration

```yaml
plugins:
  api-client:
    timeout_seconds: 30
    max_retries: 3
```

## Tools

- `http_request` - Make HTTP request

## Documentation

Full documentation: https://ftmahringer.github.io/Synapse/docs/plugins/official/api-client

## License

MIT
