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
- PCAP file generation for Wireshark analysis
- JSON Schema validation tool for message compliance checking

## Building

```bash
mvn clean package
```

This creates:
- `target/websocket-proxy-1.0.0.jar` - Main application JAR
- `target/websocket-proxy-1.0.0-standalone.jar` - Standalone JAR with all dependencies
- `target/lib/` - Directory containing all dependency JARs

## Usage

### Option 1: Using the standalone JAR (simplest)
```bash
java -jar target/websocket-proxy-1.0.0-standalone.jar \
  -r <remote-host> \
  -p <remote-port> \
  -l <local-port> \
  [-u <path>] \
  [-d <log-directory>] \
  [-s]
```

### Option 2: Using the launcher script (recommended)
```bash
./run-proxy.sh \
  -r <remote-host> \
  -p <remote-port> \
  -l <local-port> \
  [-u <path>] \
  [-d <log-directory>] \
  [-s]
```

### Option 3: Manual classpath (with lib/ folder)
```bash
java -cp "target/websocket-proxy-1.0.0.jar:target/lib/*" \
  com.websocket.proxy.WebSocketProxy \
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
./run-proxy.sh -r example.com -p 9000 -l 8080

# Proxy with WSS and custom path
./run-proxy.sh -r example.com -p 443 -l 8080 -u /ws -s

# Custom log directory
./run-proxy.sh -r localhost -p 3000 -l 8080 -d /var/log/ws-proxy

# Using standalone JAR
java -jar target/websocket-proxy-1.0.0-standalone.jar -r example.com -p 9000 -l 8080
```

## Log Files

The proxy creates three log files per connection:

1. **Raw log** (`session_<timestamp>_conn_<id>_raw.log`): Complete raw messages with timestamps
2. **JSON log** (`session_<timestamp>_conn_<id>_json.log`): Parsed and formatted JSON/JSON-RPC messages
3. **PCAP file** (`session_<timestamp>_conn_<id>.pcap`): Network capture format compatible with Wireshark

### JSON-RPC Support

The proxy automatically detects and formats JSON-RPC 2.0 messages, showing:
- Request method, ID, and parameters
- Response ID and result
- Error codes, messages, and data

### PCAP Analysis

The generated PCAP files can be opened in Wireshark or similar tools:
- Contains synthetic TCP/IP headers for compatibility
- WebSocket frames are properly formatted
- Allows filtering and analysis of WebSocket traffic
- Useful for debugging protocol issues

## JSON Schema Validation

The proxy includes a separate schema validation tool to verify that captured JSON messages conform to expected schemas:

```bash
# Using the launcher script
./run-validator.sh --log-file logs/session_*.log --schema-dir ./schemas --format detailed

# Using standalone JAR  
java -cp target/websocket-proxy-1.0.0-standalone.jar com.websocket.proxy.SchemaValidator \
  --log-file logs/session_*.log --schema-dir ./schemas --format detailed

# Using manual classpath
java -cp "target/websocket-proxy-1.0.0.jar:target/lib/*" com.websocket.proxy.SchemaValidator \
  --log-file logs/session_*.log --schema-dir ./schemas --format detailed
```

See [SCHEMA_VALIDATION.md](SCHEMA_VALIDATION.md) for detailed documentation on schema validation.

## Client Configuration

Configure your WebSocket client to connect to `ws://localhost:<local-port>` instead of the remote server. The proxy will transparently forward all traffic while logging it.