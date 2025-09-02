#!/bin/bash

# WebSocket Proxy Launcher Script
# Uses individual JAR files from lib/ directory

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAIN_JAR="$SCRIPT_DIR/target/websocket-proxy-1.0.0.jar"
LIB_DIR="$SCRIPT_DIR/target/lib"

# Check if build artifacts exist
if [ ! -f "$MAIN_JAR" ]; then
    echo "Error: Main JAR not found at $MAIN_JAR"
    echo "Please run 'mvn clean package' first"
    exit 1
fi

if [ ! -d "$LIB_DIR" ]; then
    echo "Error: Lib directory not found at $LIB_DIR"
    echo "Please run 'mvn clean package' first"
    exit 1
fi

# Build classpath from lib directory
CLASSPATH="$MAIN_JAR"
for jar in "$LIB_DIR"/*.jar; do
    if [ -f "$jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

# Run the proxy
echo "Starting WebSocket Proxy..."
echo "Classpath: $CLASSPATH"
echo ""

java -cp "$CLASSPATH" com.websocket.proxy.WebSocketProxy "$@"