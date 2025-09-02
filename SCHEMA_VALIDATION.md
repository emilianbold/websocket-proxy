# JSON Schema Validation for WebSocket Proxy

The WebSocket proxy includes a separate schema validation tool that can validate captured JSON messages against JSON Schema definitions.

## Features

- Validates JSON messages from proxy log files
- Supports JSON Schema Draft 2020-12
- JSON-RPC aware schema matching
- Multiple output formats (summary, detailed, JSON)
- Schema organization by direction and method

## Usage

### Running the Validator

```bash
java -cp target/websocket-proxy-1.0.0.jar com.websocket.proxy.SchemaValidator \
  --log-file logs/session_*.log \
  --schema-dir ./schemas \
  --format detailed
```

### Command-Line Options

- `-l, --log-file <file>`: Path to the log file to validate (required)
- `-s, --schema-dir <dir>`: Directory containing schema files (required)
- `-f, --format <format>`: Output format - `summary`, `detailed`, or `json` (default: summary)

## Schema Organization

Schemas should be organized in the following directory structure:

```
schemas/
├── client-to-server/       # Schemas for client→server messages
│   ├── default.schema.json # Default schema for all client messages
│   ├── initialize.schema.json # Schema for 'initialize' method
│   └── [method].schema.json   # Schema for specific method
├── server-to-client/       # Schemas for server→client messages
│   ├── default.schema.json # Default schema
│   ├── response.schema.json # Schema for JSON-RPC responses
│   ├── error.schema.json    # Schema for JSON-RPC errors
│   └── notification.schema.json # Schema for notifications
└── common/                 # Shared schema definitions
    └── definitions.json    # Common $ref definitions
```

## Schema Matching Logic

The validator uses the following logic to select schemas:

1. **For JSON-RPC Requests**: Matches by method name
   - Looks for `client-to-server/[method].schema.json`
   - Falls back to `client-to-server/default.schema.json`

2. **For JSON-RPC Responses**: 
   - Uses `server-to-client/response.schema.json`

3. **For JSON-RPC Errors**:
   - Uses `server-to-client/error.schema.json`

4. **For Notifications**: Messages without an `id` field
   - Uses appropriate schema based on direction

## Example Schemas

### Client Request Schema
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "jsonrpc": { "const": "2.0" },
    "method": { "type": "string" },
    "params": { "type": "object" },
    "id": { "type": ["string", "number"] }
  },
  "required": ["jsonrpc", "method", "id"]
}
```

### Server Response Schema
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "jsonrpc": { "const": "2.0" },
    "result": {},
    "id": { "type": ["string", "number", "null"] }
  },
  "required": ["jsonrpc", "result", "id"]
}
```

## Output Formats

### Summary Format
Shows validation statistics and error counts by type.

```
=== Schema Validation Summary ===
Total messages: 150
Valid messages: 145
Invalid messages: 5

Validation errors by type:
  required: 3
  type: 2

Validation success rate: 96.67%
```

### Detailed Format
Includes full error details with line numbers and paths.

```
Line 42 [2024-01-01 10:00:00.123] CLIENT_TO_SERVER
Message: {"jsonrpc":"2.0","method":"test"}
Errors:
  - required: $.id is required
    Path: $.id
```

### JSON Format
Machine-readable format for CI/CD integration.

```json
{
  "totalMessages": 150,
  "validMessages": 145,
  "invalidMessages": 5,
  "errors": [
    {
      "line": 42,
      "timestamp": "2024-01-01 10:00:00.123",
      "direction": "CLIENT_TO_SERVER",
      "message": {...},
      "violations": [...]
    }
  ]
}
```

## Integration with CI/CD

The JSON output format can be used for automated validation in CI/CD pipelines:

```bash
# Validate and check exit code
java -cp websocket-proxy.jar com.websocket.proxy.SchemaValidator \
  -l test-session.log -s schemas -f json > validation-report.json

# Parse with jq to check success rate
SUCCESS_RATE=$(jq '.validMessages / .totalMessages * 100' validation-report.json)
if (( $(echo "$SUCCESS_RATE < 95" | bc -l) )); then
  echo "Validation failed: Success rate below 95%"
  exit 1
fi
```

## Best Practices

1. **Start with permissive schemas**: Begin with basic validation and gradually add constraints
2. **Use references**: Define common types in `common/definitions.json` and use `$ref`
3. **Version your schemas**: Include version information in schema IDs
4. **Document patterns**: Add descriptions to explain validation rules
5. **Test incrementally**: Validate sample messages before applying to production logs

## Extending the Validator

The validator can be extended to support:
- Custom schema selection logic
- Additional output formats
- Real-time validation during proxying
- Schema generation from sample messages
- Validation webhooks for alerts