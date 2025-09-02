#!/bin/bash

# Example usage script for WebSocket proxy

echo "WebSocket Proxy Test Script"
echo "=========================="
echo ""

# Show help
echo "Showing help information:"
java -jar target/websocket-proxy-1.0.0.jar --help 2>/dev/null || echo "Please build the project first with: mvn clean package"

echo ""
echo "Example commands:"
echo ""
echo "1. Basic proxy (ws://echo.websocket.org):"
echo "   java -jar target/websocket-proxy-1.0.0.jar -r echo.websocket.org -p 80 -l 8080"
echo ""
echo "2. Secure WebSocket proxy:"
echo "   java -jar target/websocket-proxy-1.0.0.jar -r echo.websocket.org -p 443 -l 8080 -s"
echo ""
echo "3. Custom path and log directory:"
echo "   java -jar target/websocket-proxy-1.0.0.jar -r example.com -p 3000 -l 8080 -u /ws -d ./custom-logs"
echo ""
echo "To test the proxy:"
echo "1. Start the proxy with one of the commands above"
echo "2. Connect your WebSocket client to ws://localhost:8080"
echo "3. The proxy will forward all traffic and log it to the ./logs directory"
echo ""
echo "Log files will be created in:"
echo "- Raw log: logs/session_<timestamp>_conn_<id>_raw.log"
echo "- JSON log: logs/session_<timestamp>_conn_<id>_json.log"