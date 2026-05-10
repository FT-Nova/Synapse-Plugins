# SYNAPSE Official Plugins

Official plugins maintained by the SYNAPSE core team.

## Overview

This repository contains all official SYNAPSE plugins with guaranteed compatibility, security audits, and comprehensive support.

**Repository**: `FTMahringer/Synapse-plugins` (private)  
**Access**: Available to all SYNAPSE users  
**Quality Standards**: Strict code review and testing

## Plugins

### Core Plugins

- **[web-search](./plugins/web-search/)** - Search the web with multiple providers
- **[file-operations](./plugins/file-operations/)** - Secure file system access
- **[code-execution](./plugins/code-execution/)** - Sandboxed code execution
- **[api-client](./plugins/api-client/)** - HTTP/REST API integration

## Bundles

### Official Bundles

- **[developer-toolkit](./bundles/developer-toolkit/)** - Essential development tools

## Installation

All official plugins are pre-installed with SYNAPSE but must be enabled per agent.

```bash
# Enable plugin for an agent
synapse agent update my-agent --enable-plugin web-search

# Install bundle
synapse bundle install developer-toolkit
```

## Plugin Structure

Each plugin follows this structure:

```
plugin-name/
├── README.md           # Plugin documentation
├── plugin.yaml         # Plugin metadata
├── src/               # Plugin source code
├── tests/             # Plugin tests
└── CHANGELOG.md       # Version history
```

## Development

See [SYNAPSE Documentation - Plugin Development](https://ftmahringer.github.io/Synapse/docs/plugins/development/getting-started) for development guidelines.

## License

All official plugins are licensed under MIT unless otherwise specified.

## Support

- **Issues**: https://github.com/FTMahringer/Synapse/issues
- **Documentation**: https://ftmahringer.github.io/Synapse/
- **Discussions**: https://github.com/FTMahringer/Synapse/discussions
