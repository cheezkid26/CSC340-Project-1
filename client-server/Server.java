import java.io.*;
import java.net.*;

public class Server {
    DatagramSocket socket = null;

    public Server() {}

    public void createAndListenSocket() {
        try {
            socket = new DatagramSocket(9876);

            while (true) {
                byte[] incomingData = new byte[2048]; // Increase buffer size
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                socket.receive(incomingPacket);

                InetAddress clientAddress = incomingPacket.getAddress();
                int clientPort = incomingPacket.getPort();

                // Deserialize TOW object
                ByteArrayInputStream byteStream = new ByteArrayInputStream(incomingPacket.getData(), 0, incomingPacket.getLength());
                ObjectInputStream objectStream = new ObjectInputStream(byteStream);
                TOW receivedPacket = (TOW) objectStream.readObject();

                System.out.println("Received TOW packet:");
                System.out.println(receivedPacket);

                // Send acknowledgment response
                String reply = "thanks for the slop!!!!";
                byte[] data = reply.getBytes();
                DatagramPacket replyPacket = new DatagramPacket(data, data.length, clientAddress, clientPort);
                socket.send(replyPacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.createAndListenSocket();
    }
}
