//TOW - Transfer Over Wi-FI

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.*;

public class TOW implements Serializable {
    private final String protocolName = "TOW";
    private double protocolVersion;
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

    public TOW(int identifier, InetAddress destIP, int destPort, String data, int packetNumber) throws UnknownHostException{
        this.protocolVersion = 1.0;
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

    


    @Override
    public String toString(){
        return "TOW Packet version " + protocolVersion + ", sent from node " + identifier + " at IP " + IP + " to " + destIP + " on port " + destPort + ". \n  Message: " + data + ", time: " + timestamp;
    }
}