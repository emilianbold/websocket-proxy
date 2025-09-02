#!/bin/bash

# WebSocket Proxy Launcher Script
# Uses standalone JAR with all dependencies included

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STANDALONE_JAR="$SCRIPT_DIR/target/websocket-proxy-1.0.0-standalone.jar"

# Check if build artifacts exist
if [ ! -f "$STANDALONE_JAR" ]; then
    echo "Error: Standalone JAR not found at $STANDALONE_JAR"
    echo "Please run 'mvn clean package' first"
    exit 1
fi

# Run the proxy using the standalone JAR
echo "Starting WebSocket Proxy..."
java -jar "$STANDALONE_JAR" "$@"