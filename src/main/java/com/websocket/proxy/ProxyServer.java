package com.websocket.proxy;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyServer extends WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(ProxyServer.class);
    
    private final URI remoteUri;
    private final String logDirectory;
    private final String sessionId;
    private final Map<WebSocket, ProxyConnection> connections = new ConcurrentHashMap<>();
    
    public ProxyServer(InetSocketAddress address, URI remoteUri, String logDirectory, String sessionId) {
        super(address);
        this.remoteUri = remoteUri;
        this.logDirectory = logDirectory;
        this.sessionId = sessionId;
    }
    
    @Override
    public void onOpen(WebSocket clientConn, ClientHandshake handshake) {
        logger.info("New client connection from: {}", clientConn.getRemoteSocketAddress());
        
        try {
            // Get the local port from the client connection
            int clientPort = clientConn.getRemoteSocketAddress().getPort();
            
            ProxyConnection proxyConnection = new ProxyConnection(
                clientConn, 
                remoteUri, 
                logDirectory, 
                sessionId,
                connections.size() + 1,
                clientPort
            );
            
            connections.put(clientConn, proxyConnection);
            proxyConnection.connect();
            
        } catch (Exception e) {
            logger.error("Failed to establish proxy connection", e);
            clientConn.close(1011, "Failed to connect to remote server");
        }
    }
    
    @Override
    public void onMessage(WebSocket clientConn, String message) {
        ProxyConnection proxyConnection = connections.get(clientConn);
        if (proxyConnection != null) {
            proxyConnection.sendToServer(message);
        } else {
            logger.warn("Received message from unknown client connection");
        }
    }
    
    @Override
    public void onMessage(WebSocket clientConn, ByteBuffer message) {
        ProxyConnection proxyConnection = connections.get(clientConn);
        if (proxyConnection != null) {
            proxyConnection.sendToServer(message);
        } else {
            logger.warn("Received binary message from unknown client connection");
        }
    }
    
    @Override
    public void onClose(WebSocket clientConn, int code, String reason, boolean remote) {
        logger.info("Client connection closed: {} - {}", code, reason);
        
        ProxyConnection proxyConnection = connections.remove(clientConn);
        if (proxyConnection != null) {
            proxyConnection.close();
        }
    }
    
    @Override
    public void onError(WebSocket clientConn, Exception ex) {
        logger.error("WebSocket error on client connection", ex);
        
        if (clientConn != null) {
            ProxyConnection proxyConnection = connections.get(clientConn);
            if (proxyConnection != null) {
                proxyConnection.close();
            }
        }
    }
    
    @Override
    public void onStart() {
        logger.info("Proxy server started on: {}", getAddress());
    }
}