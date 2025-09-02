# WebSocket Proxy with JSON-RPC Logging

A transparent WebSocket proxy that logs all traffic, with special support for JSON-RPC 2.0 messages.

## Features

- Transparent WebSocket proxying between client and server
- Full session logging with timestamps
- JSON-RPC 2.0 message parsing and pretty-printing
- Handles partial JSON messages across packets
- Supports both text and binary WebSocket messages
- Concurrent connection support
- Separate raw and JSON-formatted log files

## Building

```bash
mvn clean package
```

This creates an executable JAR in `target/websocket-proxy-1.0.0.jar`

## Usage

```bash
java -jar target/websocket-proxy-1.0.0.jar \
  -r <remote-host> \
  -p <remote-port> \
  -l <local-port> \
  [-u <path>] \
  [-d <log-directory>] \
  [-s]
```

### Parameters

- `-r, --remote <host>`: Remote WebSocket server hostname (required)
- `-p, --remote-port <port>`: Remote WebSocket server port (required)
- `-l, --local-port <port>`: Local proxy port to listen on (required)
- `-u, --path <path>`: WebSocket path on remote server (default: `/`)
- `-d, --log-dir <directory>`: Directory for log files (default: `./logs`)
- `-s, --ssl`: Use WSS (secure WebSocket) for remote connection

### Examples

```bash
# Proxy local port 8080 to remote WebSocket server at example.com:9000
java -jar target/websocket-proxy-1.0.0.jar -r example.com -p 9000 -l 8080

# Proxy with WSS and custom path
java -jar target/websocket-proxy-1.0.0.jar -r example.com -p 443 -l 8080 -u /ws -s

# Custom log directory
java -jar target/websocket-proxy-1.0.0.jar -r localhost -p 3000 -l 8080 -d /var/log/ws-proxy
```

## Log Files

The proxy creates two log files per connection:

1. **Raw log** (`session_<timestamp>_conn_<id>_raw.log`): Complete raw messages
2. **JSON log** (`session_<timestamp>_conn_<id>_json.log`): Parsed and formatted JSON/JSON-RPC messages

### JSON-RPC Support

The proxy automatically detects and formats JSON-RPC 2.0 messages, showing:
- Request method, ID, and parameters
- Response ID and result
- Error codes, messages, and data

## Client Configuration

Configure your WebSocket client to connect to `ws://localhost:<local-port>` instead of the remote server. The proxy will transparently forward all traffic while logging it.