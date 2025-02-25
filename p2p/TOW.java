//TOW - Transfer Over Wi-FI

import java.io.IOException;
import java.net.*;

public class TOW{
    private final String protocolName = "TOW";
    private double protocolVersion;
    private byte[] length;
    
    private InetAddress IP;
    private int identifier;
    private int port;

    private int packetNumber;

    private InetAddress destIP;
    private int destPort;

    private String data;
    private byte[] dataBytes;

    public TOW(int identifier, InetAddress destIP, int destPort, String data) throws UnknownHostException{
        this.protocolVersion = 1.0;
        this.IP = InetAddress.getLocalHost();
        this.length = new byte[1024];
        this.identifier = identifier;
        this.port = 5000;

        this.packetNumber = 1;

        this.destIP = IP;
        this.destPort = port;

        this.dataBytes = data.getBytes();
    }


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

    //public getDataString(){
    //    return new String(dataBytes.getData());
    //}

    public String getProtocolName(){
        return protocolName;

    }

    public double getVersion(){
        return protocolVersion;
    }


    public static void main(String[] args) throws UnknownHostException {
        
        TOW packet = new TOW(1, InetAddress.getLocalHost(), 9876, "file1, file2, file3");
        
        System.out.println("Sending packet...");
        System.out.println(packet.getProtocolName() + " protocol, version " + packet.getVersion());
        System.out.println("Destination: IP " + packet.getDestIP() + " on port " + packet.getDestPort());
        System.out.println("Sending data: " + packet.getData());
    }
}