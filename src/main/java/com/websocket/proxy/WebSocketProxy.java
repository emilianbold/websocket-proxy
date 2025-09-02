package com.websocket.proxy;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WebSocketProxy {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketProxy.class);
    
    public static void main(String[] args) {
        Options options = new Options();
        
        Option remoteHost = new Option("r", "remote", true, "Remote WebSocket host");
        remoteHost.setRequired(true);
        options.addOption(remoteHost);
        
        Option remotePort = new Option("p", "remote-port", true, "Remote WebSocket port");
        remotePort.setRequired(true);
        options.addOption(remotePort);
        
        Option localPort = new Option("l", "local-port", true, "Local proxy port");
        localPort.setRequired(true);
        options.addOption(localPort);
        
        Option remotePath = new Option("u", "path", true, "Remote WebSocket path (default: /)");
        remotePath.setRequired(false);
        options.addOption(remotePath);
        
        Option logDir = new Option("d", "log-dir", true, "Log directory (default: ./logs)");
        logDir.setRequired(false);
        options.addOption(logDir);
        
        Option useSSL = new Option("s", "ssl", false, "Use WSS (secure WebSocket)");
        useSSL.setRequired(false);
        options.addOption(useSSL);
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;
        
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            formatter.printHelp("websocket-proxy", options);
            System.exit(1);
            return;
        }
        
        String remote = cmd.getOptionValue("remote");
        int rPort = Integer.parseInt(cmd.getOptionValue("remote-port"));
        int lPort = Integer.parseInt(cmd.getOptionValue("local-port"));
        String path = cmd.getOptionValue("path", "/");
        String logDirectory = cmd.getOptionValue("log-dir", "./logs");
        boolean ssl = cmd.hasOption("ssl");
        
        String protocol = ssl ? "wss" : "ws";
        String remoteUri = String.format("%s://%s:%d%s", protocol, remote, rPort, path);
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String sessionId = dateFormat.format(new Date());
        
        try {
            URI uri = new URI(remoteUri);
            InetSocketAddress localAddress = new InetSocketAddress("0.0.0.0", lPort);
            
            ProxyServer proxyServer = new ProxyServer(localAddress, uri, logDirectory, sessionId);
            proxyServer.start();
            
            logger.info("WebSocket proxy started on port {} forwarding to {}", lPort, remoteUri);
            logger.info("Session logs will be saved to: {}/session_{}_*.log", logDirectory, sessionId);
            logger.info("Press Ctrl+C to stop the proxy");
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down proxy server...");
                try {
                    proxyServer.stop(1000);
                } catch (InterruptedException e) {
                    logger.error("Error during shutdown", e);
                }
            }));
            
            Thread.currentThread().join();
            
        } catch (Exception e) {
            logger.error("Failed to start proxy server", e);
            System.exit(1);
        }
    }
}