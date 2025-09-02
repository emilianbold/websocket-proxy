package com.websocket.proxy;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

public class PcapWriter implements AutoCloseable {
    private static final int PCAP_MAGIC = 0xa1b2c3d4;
    private static final short PCAP_VERSION_MAJOR = 2;
    private static final short PCAP_VERSION_MINOR = 4;
    private static final int PCAP_SNAPLEN = 65535;
    private static final int PCAP_NETWORK = 1; // Ethernet
    
    private static final byte[] ETHERNET_HEADER = {
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, // Dest MAC
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, // Source MAC  
        (byte)0x08, (byte)0x00  // EtherType: IPv4
    };
    
    private final DataOutputStream output;
    private final String clientIp = "127.0.0.1";
    private final String serverIp;
    private final int clientPort;
    private final int serverPort;
    
    private final AtomicInteger tcpSeqClient = new AtomicInteger(1000);
    private final AtomicInteger tcpSeqServer = new AtomicInteger(2000);
    private final AtomicInteger ipId = new AtomicInteger(1);
    
    private boolean handshakeComplete = false;
    
    public PcapWriter(String filename, String serverHost, int clientPort, int serverPort) throws IOException {
        this.output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filename)));
        this.serverIp = resolveToIp(serverHost);
        this.clientPort = clientPort;
        this.serverPort = serverPort;
        
        writePcapHeader();
    }
    
    private String resolveToIp(String host) {
        // Simplified - just use a fake IP for the server
        // In a real implementation, you'd resolve the hostname
        return "192.168.1.100";
    }
    
    private void writePcapHeader() throws IOException {
        output.writeInt(PCAP_MAGIC);
        output.writeShort(PCAP_VERSION_MAJOR);
        output.writeShort(PCAP_VERSION_MINOR);
        output.writeInt(0); // thiszone
        output.writeInt(0); // sigfigs
        output.writeInt(PCAP_SNAPLEN);
        output.writeInt(PCAP_NETWORK);
        output.flush();
    }
    
    public synchronized void writeWebSocketHandshake() throws IOException {
        if (handshakeComplete) return;
        
        // Simplified TCP handshake (SYN, SYN-ACK, ACK)
        long timestamp = System.currentTimeMillis();
        
        // SYN from client
        writeTcpPacket(timestamp, clientIp, clientPort, serverIp, serverPort, 
                      tcpSeqClient.get(), 0, (byte)0x02, new byte[0]);
        
        // SYN-ACK from server
        writeTcpPacket(timestamp + 1, serverIp, serverPort, clientIp, clientPort,
                      tcpSeqServer.get(), tcpSeqClient.get() + 1, (byte)0x12, new byte[0]);
        
        // ACK from client
        writeTcpPacket(timestamp + 2, clientIp, clientPort, serverIp, serverPort,
                      tcpSeqClient.get() + 1, tcpSeqServer.get() + 1, (byte)0x10, new byte[0]);
        
        tcpSeqClient.incrementAndGet();
        tcpSeqServer.incrementAndGet();
        
        // WebSocket HTTP upgrade request
        String wsHandshake = "GET / HTTP/1.1\r\n" +
                           "Host: " + serverIp + ":" + serverPort + "\r\n" +
                           "Upgrade: websocket\r\n" +
                           "Connection: Upgrade\r\n" +
                           "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                           "Sec-WebSocket-Version: 13\r\n\r\n";
        
        writeTcpPacket(timestamp + 10, clientIp, clientPort, serverIp, serverPort,
                      tcpSeqClient.get(), tcpSeqServer.get(), (byte)0x18, wsHandshake.getBytes());
        tcpSeqClient.addAndGet(wsHandshake.length());
        
        // WebSocket HTTP upgrade response
        String wsResponse = "HTTP/1.1 101 Switching Protocols\r\n" +
                          "Upgrade: websocket\r\n" +
                          "Connection: Upgrade\r\n" +
                          "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n\r\n";
        
        writeTcpPacket(timestamp + 20, serverIp, serverPort, clientIp, clientPort,
                      tcpSeqServer.get(), tcpSeqClient.get(), (byte)0x18, wsResponse.getBytes());
        tcpSeqServer.addAndGet(wsResponse.length());
        
        handshakeComplete = true;
        output.flush();
    }
    
    public synchronized void writeClientToServer(byte[] data) throws IOException {
        if (!handshakeComplete) {
            writeWebSocketHandshake();
        }
        
        byte[] wsFrame = createWebSocketFrame(data, true);
        writeTcpPacket(System.currentTimeMillis(), clientIp, clientPort, serverIp, serverPort,
                      tcpSeqClient.get(), tcpSeqServer.get(), (byte)0x18, wsFrame);
        tcpSeqClient.addAndGet(wsFrame.length);
        output.flush();
    }
    
    public synchronized void writeServerToClient(byte[] data) throws IOException {
        if (!handshakeComplete) {
            writeWebSocketHandshake();
        }
        
        byte[] wsFrame = createWebSocketFrame(data, false);
        writeTcpPacket(System.currentTimeMillis(), serverIp, serverPort, clientIp, clientPort,
                      tcpSeqServer.get(), tcpSeqClient.get(), (byte)0x18, wsFrame);
        tcpSeqServer.addAndGet(wsFrame.length);
        output.flush();
    }
    
    private byte[] createWebSocketFrame(byte[] payload, boolean mask) {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        
        // FIN = 1, RSV = 0, Opcode = 1 (text) or 2 (binary)
        boolean isText = isValidUtf8(payload);
        frame.write(0x80 | (isText ? 0x01 : 0x02));
        
        // Mask bit and payload length
        int len = payload.length;
        if (len < 126) {
            frame.write((mask ? 0x80 : 0x00) | len);
        } else if (len < 65536) {
            frame.write((mask ? 0x80 : 0x00) | 126);
            frame.write((len >> 8) & 0xFF);
            frame.write(len & 0xFF);
        } else {
            frame.write((mask ? 0x80 : 0x00) | 127);
            // For simplicity, write 8 bytes for extended length
            for (int i = 7; i >= 0; i--) {
                frame.write((len >> (i * 8)) & 0xFF);
            }
        }
        
        // Masking key (if client->server)
        byte[] maskKey = null;
        if (mask) {
            maskKey = new byte[]{0x12, 0x34, 0x56, 0x78};
            frame.write(maskKey, 0, 4);
        }
        
        // Payload (masked if client->server)
        if (mask && maskKey != null) {
            byte[] maskedPayload = new byte[payload.length];
            for (int i = 0; i < payload.length; i++) {
                maskedPayload[i] = (byte)(payload[i] ^ maskKey[i % 4]);
            }
            frame.write(maskedPayload, 0, maskedPayload.length);
        } else {
            frame.write(payload, 0, payload.length);
        }
        
        return frame.toByteArray();
    }
    
    private boolean isValidUtf8(byte[] data) {
        try {
            new String(data, "UTF-8");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void writeTcpPacket(long timestamp, String srcIp, int srcPort, 
                                String dstIp, int dstPort, int seqNum, int ackNum,
                                byte tcpFlags, byte[] payload) throws IOException {
        
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        
        // Ethernet header
        packet.write(ETHERNET_HEADER);
        
        // IP header (20 bytes)
        packet.write(0x45); // Version (4) + IHL (5)
        packet.write(0x00); // Type of Service
        int ipTotalLength = 20 + 20 + payload.length; // IP + TCP + payload
        packet.write((ipTotalLength >> 8) & 0xFF);
        packet.write(ipTotalLength & 0xFF);
        int id = ipId.getAndIncrement();
        packet.write((id >> 8) & 0xFF);
        packet.write(id & 0xFF);
        packet.write(0x40); // Flags (Don't Fragment)
        packet.write(0x00); // Fragment offset
        packet.write(0x40); // TTL
        packet.write(0x06); // Protocol: TCP
        packet.write(0x00); // Checksum (placeholder)
        packet.write(0x00);
        
        // Source IP
        writeIpAddress(packet, srcIp);
        // Destination IP
        writeIpAddress(packet, dstIp);
        
        // TCP header (20 bytes)
        packet.write((srcPort >> 8) & 0xFF);
        packet.write(srcPort & 0xFF);
        packet.write((dstPort >> 8) & 0xFF);
        packet.write(dstPort & 0xFF);
        
        // Sequence number
        packet.write((seqNum >> 24) & 0xFF);
        packet.write((seqNum >> 16) & 0xFF);
        packet.write((seqNum >> 8) & 0xFF);
        packet.write(seqNum & 0xFF);
        
        // Acknowledgment number
        packet.write((ackNum >> 24) & 0xFF);
        packet.write((ackNum >> 16) & 0xFF);
        packet.write((ackNum >> 8) & 0xFF);
        packet.write(ackNum & 0xFF);
        
        packet.write(0x50); // Data offset (5 * 4 = 20 bytes)
        packet.write(tcpFlags); // Flags
        packet.write(0xFF); // Window size
        packet.write(0xFF);
        packet.write(0x00); // Checksum (placeholder)
        packet.write(0x00);
        packet.write(0x00); // Urgent pointer
        packet.write(0x00);
        
        // Payload
        packet.write(payload);
        
        byte[] packetData = packet.toByteArray();
        
        // Write PCAP packet header
        int seconds = (int)(timestamp / 1000);
        int microseconds = (int)((timestamp % 1000) * 1000);
        
        output.writeInt(seconds);
        output.writeInt(microseconds);
        output.writeInt(packetData.length); // Captured length
        output.writeInt(packetData.length); // Original length
        
        // Write packet data
        output.write(packetData);
    }
    
    private void writeIpAddress(ByteArrayOutputStream out, String ip) {
        String[] parts = ip.split("\\.");
        for (String part : parts) {
            out.write(Integer.parseInt(part) & 0xFF);
        }
    }
    
    @Override
    public void close() throws IOException {
        if (output != null) {
            output.close();
        }
    }
}