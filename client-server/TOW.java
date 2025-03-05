//TOW - Transfer Over Wi-FI

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TOW implements Serializable {
    private final String protocolName = "TOW";
    private final double protocolVersion = 1.0;
    private byte[] length;
    
    private InetAddress IP;
    private int identifier;
    private int port;
    private long timestamp;

    private int packetNumber;

    private InetAddress destIP;
    private int destPort;

    private String data;
    private byte[] dataBytes;
    private int dataLength;


    private boolean[] clientStatus = new boolean[6];

    //sent from client
    public TOW(int identifier, InetAddress destIP, int destPort, String data, int packetNumber) throws UnknownHostException{
        this.IP = InetAddress.getLocalHost();
        this.length = new byte[1024];
        this.identifier = identifier;
        this.port = 5000;
        this.timestamp = System.currentTimeMillis();

        this.packetNumber = packetNumber;

        this.destIP = IP;
        this.destPort = port;

        this.data = data;
        this.dataBytes = data.getBytes();
        this.dataLength = dataBytes.length;
    }

    //response from server
    public TOW(int port, InetAddress destIP, int destPort, boolean[] aliveClients){
        this.length = new byte[1024];
        this.port = 9876;
        this.timestamp = System.currentTimeMillis();
        this.identifier = 0; // server's ID defaults to 0
        clientStatus = aliveClients;
    }

    //simple getters and setters
    public void setDestination(InetAddress destIP, int destPort){
        this.destIP = destIP;
        this.destPort = destPort;
    }

    public void setData(String newData){
        this.data = newData;
    }

    public InetAddress getDestIP(){
        return this.destIP;
    }

    public InetAddress getSenderIP(){
        return this.IP;
    }
    
    public int getSenderPort(){
        return port;
    }

    public int getDestPort(){
        return this.destPort;
    }

    public byte[] getData(){
        return this.dataBytes;
    }

    public String getProtocolName(){
        return protocolName;
    }

    public int getIdentifier() {
        return identifier;
    }

    public double getVersion(){
        return protocolVersion;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getPacketNumber(){
        return packetNumber;
    }

    public boolean[] getClientStatuses(){
        return clientStatus;
    }

    public String readableTimestamp(){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        return formatter.format(Instant.ofEpochMilli(timestamp));
    }
    

    @Override
    public String toString(){
        return "TOW Packet version " + protocolVersion + ", sent from node " + identifier + " at IP " + IP + " to " + destIP + " on port " + destPort + ". \n  Message: " + data + ", time: " + readableTimestamp();
    }
}