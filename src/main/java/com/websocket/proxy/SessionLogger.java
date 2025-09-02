package com.websocket.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

public class SessionLogger {
    private static final Logger logger = LoggerFactory.getLogger(SessionLogger.class);
    
    private final ObjectMapper objectMapper;
    private final PrintWriter rawLogWriter;
    private final PrintWriter jsonLogWriter;
    private final SimpleDateFormat timestampFormat;
    private final int connectionId;
    
    private StringBuilder partialJsonBuffer = new StringBuilder();
    
    public SessionLogger(String logDirectory, String sessionId, int connectionId) {
        this.connectionId = connectionId;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        
        File logDir = new File(logDirectory);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        String rawLogFile = String.format("%s/session_%s_conn_%d_raw.log", 
            logDirectory, sessionId, connectionId);
        String jsonLogFile = String.format("%s/session_%s_conn_%d_json.log", 
            logDirectory, sessionId, connectionId);
        
        try {
            this.rawLogWriter = new PrintWriter(new FileWriter(rawLogFile, true));
            this.jsonLogWriter = new PrintWriter(new FileWriter(jsonLogFile, true));
            
            logEvent("SESSION_START", String.format("Connection #%d logging started", connectionId));
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to create log files", e);
        }
    }
    
    public synchronized void logMessage(String direction, String message) {
        String timestamp = timestampFormat.format(new Date());
        
        rawLogWriter.printf("[%s] [CONN_%d] [%s] %s%n", timestamp, connectionId, direction, message);
        rawLogWriter.flush();
        
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            
            if (isJsonRpcMessage(jsonNode)) {
                logJsonRpcMessage(timestamp, direction, jsonNode);
            } else {
                logJsonMessage(timestamp, direction, jsonNode);
            }
            
            partialJsonBuffer.setLength(0);
            
        } catch (Exception e) {
            partialJsonBuffer.append(message);
            
            if (partialJsonBuffer.length() > 0) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(partialJsonBuffer.toString());
                    
                    if (isJsonRpcMessage(jsonNode)) {
                        logJsonRpcMessage(timestamp, direction, jsonNode);
                    } else {
                        logJsonMessage(timestamp, direction, jsonNode);
                    }
                    
                    partialJsonBuffer.setLength(0);
                    
                } catch (Exception ex) {
                    if (partialJsonBuffer.length() > 100000) {
                        logger.warn("Partial JSON buffer too large, clearing");
                        partialJsonBuffer.setLength(0);
                    }
                }
            }
        }
    }
    
    public synchronized void logBinaryMessage(String direction, byte[] data) {
        String timestamp = timestampFormat.format(new Date());
        String base64Data = Base64.getEncoder().encodeToString(data);
        
        rawLogWriter.printf("[%s] [CONN_%d] [%s] [BINARY] %d bytes: %s%n", 
            timestamp, connectionId, direction, data.length, base64Data);
        rawLogWriter.flush();
        
        jsonLogWriter.printf("[%s] [CONN_%d] [%s] [BINARY] %d bytes%n", 
            timestamp, connectionId, direction, data.length);
        jsonLogWriter.flush();
    }
    
    public synchronized void logEvent(String eventType, String description) {
        String timestamp = timestampFormat.format(new Date());
        
        rawLogWriter.printf("[%s] [CONN_%d] [EVENT] [%s] %s%n", 
            timestamp, connectionId, eventType, description);
        rawLogWriter.flush();
        
        jsonLogWriter.printf("[%s] [CONN_%d] [EVENT] [%s] %s%n", 
            timestamp, connectionId, eventType, description);
        jsonLogWriter.flush();
    }
    
    private boolean isJsonRpcMessage(JsonNode node) {
        return node.has("jsonrpc") && 
               (node.has("method") || node.has("result") || node.has("error"));
    }
    
    private void logJsonRpcMessage(String timestamp, String direction, JsonNode jsonNode) {
        jsonLogWriter.printf("[%s] [CONN_%d] [%s] [JSON-RPC]%n", timestamp, connectionId, direction);
        
        if (jsonNode.has("method")) {
            String method = jsonNode.get("method").asText();
            JsonNode id = jsonNode.get("id");
            jsonLogWriter.printf("  Type: Request%n");
            jsonLogWriter.printf("  Method: %s%n", method);
            if (id != null) {
                jsonLogWriter.printf("  ID: %s%n", id.toString());
            }
            if (jsonNode.has("params")) {
                jsonLogWriter.printf("  Params: %s%n", jsonNode.get("params").toString());
            }
        } else if (jsonNode.has("result")) {
            JsonNode id = jsonNode.get("id");
            jsonLogWriter.printf("  Type: Response%n");
            if (id != null) {
                jsonLogWriter.printf("  ID: %s%n", id.toString());
            }
            jsonLogWriter.printf("  Result: %s%n", jsonNode.get("result").toString());
        } else if (jsonNode.has("error")) {
            JsonNode id = jsonNode.get("id");
            JsonNode error = jsonNode.get("error");
            jsonLogWriter.printf("  Type: Error%n");
            if (id != null) {
                jsonLogWriter.printf("  ID: %s%n", id.toString());
            }
            if (error.has("code")) {
                jsonLogWriter.printf("  Error Code: %d%n", error.get("code").asInt());
            }
            if (error.has("message")) {
                jsonLogWriter.printf("  Error Message: %s%n", error.get("message").asText());
            }
            if (error.has("data")) {
                jsonLogWriter.printf("  Error Data: %s%n", error.get("data").toString());
            }
        }
        
        jsonLogWriter.printf("  Full Message:%n");
        try {
            String prettyJson = objectMapper.writeValueAsString(jsonNode);
            for (String line : prettyJson.split("\n")) {
                jsonLogWriter.printf("    %s%n", line);
            }
        } catch (Exception e) {
            jsonLogWriter.printf("    %s%n", jsonNode.toString());
        }
        
        jsonLogWriter.flush();
    }
    
    private void logJsonMessage(String timestamp, String direction, JsonNode jsonNode) {
        jsonLogWriter.printf("[%s] [CONN_%d] [%s] [JSON]%n", timestamp, connectionId, direction);
        
        try {
            String prettyJson = objectMapper.writeValueAsString(jsonNode);
            for (String line : prettyJson.split("\n")) {
                jsonLogWriter.printf("  %s%n", line);
            }
        } catch (Exception e) {
            jsonLogWriter.printf("  %s%n", jsonNode.toString());
        }
        
        jsonLogWriter.flush();
    }
    
    public synchronized void close() {
        logEvent("SESSION_END", String.format("Connection #%d logging stopped", connectionId));
        
        if (rawLogWriter != null) {
            rawLogWriter.close();
        }
        if (jsonLogWriter != null) {
            jsonLogWriter.close();
        }
    }
}