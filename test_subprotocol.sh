#!/bin/bash

# Test script to verify WebSocket subprotocol forwarding

echo "Testing WebSocket subprotocol support..."

# Build the project if not already built
if [ ! -f "target/websocket-proxy-1.0.0-standalone.jar" ]; then
    echo "Building project..."
    mvn clean package
fi

# Start the proxy server in the background
echo "Starting proxy server on port 8080..."
java -jar target/websocket-proxy-1.0.0-standalone.jar proxy 8080 ws://echo.websocket.org/ test_logs &
PROXY_PID=$!

# Wait for server to start
sleep 2

# Test with wscat or a simple test client
echo "Testing connection with subprotocol 'chat'..."
echo "Note: This test requires wscat or another WebSocket client that supports subprotocols"
echo ""
echo "To test manually:"
echo "1. Install wscat: npm install -g wscat"
echo "2. Connect with subprotocol: wscat -c ws://localhost:8080 -s chat"
echo "3. Check logs in test_logs/ directory for subprotocol forwarding"
echo ""
echo "Press Enter to stop the proxy server..."
read

# Kill the proxy server
kill $PROXY_PID
echo "Proxy server stopped."