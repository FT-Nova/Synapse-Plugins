# Telegram Channel Plugin

Official SYNAPSE channel plugin for Telegram.

## Features

- Long-polling mode (default) — no public URL required
- Webhook mode — register a public HTTPS endpoint
- Send and receive text messages
- Virtual thread-based polling loop

## Configuration

```yaml
bot_token: "<from @BotFather>"
webhook_url: ""  # omit for long-polling
polling_interval_ms: 2000
```

## Build

```bash
mvn package
```

Produces `target/telegram-channel-1.0.0.jar` — install via dashboard or CLI.

## License

MIT
