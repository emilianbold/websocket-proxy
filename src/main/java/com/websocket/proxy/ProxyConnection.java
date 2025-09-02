package com.websocket.proxy;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;

public class ProxyConnection {
    private static final Logger logger = LoggerFactory.getLogger(ProxyConnection.class);
    
    private final WebSocket clientConnection;
    private final URI remoteUri;
    private final SessionLogger sessionLogger;
    private WebSocketClient serverConnection;
    private final int connectionId;
    
    public ProxyConnection(WebSocket clientConnection, URI remoteUri, String logDirectory, 
                          String sessionId, int connectionId, int clientPort) {
        this.clientConnection = clientConnection;
        this.remoteUri = remoteUri;
        this.connectionId = connectionId;
        
        // Extract server host and port from URI
        String serverHost = remoteUri.getHost();
        int serverPort = remoteUri.getPort();
        if (serverPort == -1) {
            serverPort = "wss".equals(remoteUri.getScheme()) ? 443 : 80;
        }
        
        this.sessionLogger = new SessionLogger(logDirectory, sessionId, connectionId,
                                              serverHost, clientPort, serverPort);
    }
    
    public void connect() {
        logger.info("Establishing connection #{} to remote server: {}", connectionId, remoteUri);
        
        serverConnection = new WebSocketClient(remoteUri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                logger.info("Connection #{} established to remote server", connectionId);
                sessionLogger.logEvent("CONNECTION_ESTABLISHED", "Connected to " + remoteUri);
            }
            
            @Override
            public void onMessage(String message) {
                sessionLogger.logMessage("SERVER_TO_CLIENT", message);
                
                if (clientConnection.isOpen()) {
                    clientConnection.send(message);
                }
            }
            
            @Override
            public void onMessage(ByteBuffer bytes) {
                byte[] data = new byte[bytes.remaining()];
                bytes.get(data);
                sessionLogger.logBinaryMessage("SERVER_TO_CLIENT", data);
                
                if (clientConnection.isOpen()) {
                    clientConnection.send(bytes);
                }
            }
            
            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.info("Connection #{} to remote server closed: {} - {}", connectionId, code, reason);
                sessionLogger.logEvent("SERVER_CONNECTION_CLOSED", 
                    String.format("Code: %d, Reason: %s", code, reason));
                
                if (clientConnection.isOpen()) {
                    clientConnection.close(code, reason);
                }
            }
            
            @Override
            public void onError(Exception ex) {
                logger.error("Connection #{} error with remote server", connectionId, ex);
                sessionLogger.logEvent("SERVER_CONNECTION_ERROR", ex.getMessage());
                
                if (clientConnection.isOpen()) {
                    clientConnection.close(1011, "Remote server error: " + ex.getMessage());
                }
            }
        };
        
        serverConnection.connect();
    }
    
    public void sendToServer(String message) {
        sessionLogger.logMessage("CLIENT_TO_SERVER", message);
        
        if (serverConnection != null && serverConnection.isOpen()) {
            serverConnection.send(message);
        } else {
            logger.warn("Connection #{}: Cannot send message to server - connection not open", connectionId);
        }
    }
    
    public void sendToServer(ByteBuffer message) {
        byte[] data = new byte[message.remaining()];
        message.get(data);
        sessionLogger.logBinaryMessage("CLIENT_TO_SERVER", data);
        message.rewind();
        
        if (serverConnection != null && serverConnection.isOpen()) {
            serverConnection.send(message);
        } else {
            logger.warn("Connection #{}: Cannot send binary message to server - connection not open", connectionId);
        }
    }
    
    public void close() {
        logger.info("Closing proxy connection #{}", connectionId);
        sessionLogger.logEvent("PROXY_CONNECTION_CLOSED", "Proxy connection closed");
        
        if (serverConnection != null && serverConnection.isOpen()) {
            serverConnection.close();
        }
        
        sessionLogger.close();
    }
}