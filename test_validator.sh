#!/bin/bash

echo "Schema Validator Test Script"
echo "============================"
echo ""

# Create a sample log file for testing
echo "Creating sample log file..."
cat > test_session.log << 'EOF'
[2024-01-01 10:00:00.123] [CONN_1] [CLIENT_TO_SERVER] {"jsonrpc":"2.0","method":"initialize","params":{"clientId":"test-client","clientVersion":"1.0.0"},"id":1}
[2024-01-01 10:00:00.234] [CONN_1] [SERVER_TO_CLIENT] {"jsonrpc":"2.0","result":{"serverVersion":"2.0.0","capabilities":{}},"id":1}
[2024-01-01 10:00:01.345] [CONN_1] [CLIENT_TO_SERVER] {"jsonrpc":"2.0","method":"getData","params":{"key":"value"},"id":2}
[2024-01-01 10:00:01.456] [CONN_1] [SERVER_TO_CLIENT] {"jsonrpc":"2.0","result":{"data":"test"},"id":2}
[2024-01-01 10:00:02.567] [CONN_1] [CLIENT_TO_SERVER] {"jsonrpc":"2.0","method":"invalidRequest"}
[2024-01-01 10:00:02.678] [CONN_1] [SERVER_TO_CLIENT] {"jsonrpc":"2.0","error":{"code":-32600,"message":"Invalid Request"},"id":null}
[2024-01-01 10:00:03.789] [CONN_1] [SERVER_TO_CLIENT] {"jsonrpc":"2.0","method":"notification","params":{"event":"update"}}
[2024-01-01 10:00:04.890] [CONN_1] [EVENT] [CONNECTION_CLOSED] Connection closed
[2024-01-01 10:00:05.901] [CONN_1] [CLIENT_TO_SERVER] [BINARY] 10 bytes: SGVsbG8gV29ybGQ=
EOF

echo "Sample log file created: test_session.log"
echo ""

# Compile if needed
if [ ! -f "target/classes/com/websocket/proxy/SchemaValidator.class" ]; then
    echo "Compiling project..."
    mvn compile
fi

echo "Running schema validator with different output formats:"
echo ""

echo "1. Summary format:"
echo "-----------------"
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
    com.websocket.proxy.SchemaValidator \
    --log-file test_session.log \
    --schema-dir schemas \
    --format summary

echo ""
echo "2. Detailed format:"
echo "-------------------"
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
    com.websocket.proxy.SchemaValidator \
    --log-file test_session.log \
    --schema-dir schemas \
    --format detailed

echo ""
echo "3. JSON format (truncated for display):"
echo "---------------------------------------"
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
    com.websocket.proxy.SchemaValidator \
    --log-file test_session.log \
    --schema-dir schemas \
    --format json | head -20

echo ""
echo "Test completed. The validator can be used to ensure JSON messages"
echo "conform to expected schemas during WebSocket communication."