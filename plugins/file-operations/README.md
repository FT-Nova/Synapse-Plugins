# File Operations Plugin

Secure file system access for SYNAPSE agents.

## Features

- Read/write files
- List directories
- Search files
- Archive operations (zip, tar)
- Path validation & security

## Installation

```bash
synapse agent update my-agent --enable-plugin file-operations
```

## Configuration

```yaml
plugins:
  file-operations:
    base_path: /workspace
    max_file_size_mb: 100
```

## Tools

- `read_file` - Read file contents
- `write_file` - Write to file
- `list_directory` - List directory contents
- `search_files` - Search for files
- `create_archive` - Create zip/tar archive
- `extract_archive` - Extract archive

## Documentation

Full documentation: https://ftmahringer.github.io/Synapse/docs/plugins/official/file-operations

## License

MIT
