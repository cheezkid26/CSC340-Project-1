import java.io.*;
import java.net.*;
import java.util.*;

public class UDPServer {
    DatagramSocket socket;
    Map<Integer, TOW> clientStatus = new HashMap<>();
    long TIMEOUT = 30000; // 30 seconds timeout

    public UDPServer() throws SocketException {
        socket = new DatagramSocket(9876);
    }

    public void listenForClients() {
        System.out.println("Server started, listening for clients...");
        
        while (true) {
            try {
                byte[] incomingData = new byte[2048];
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                socket.receive(incomingPacket);
                
                InetAddress clientAddress = incomingPacket.getAddress();
                int clientPort = incomingPacket.getPort();

                // Deserialize the received TOW object
                ByteArrayInputStream byteStream = new ByteArrayInputStream(incomingPacket.getData(), 0, incomingPacket.getLength());
                ObjectInputStream objectStream = new ObjectInputStream(byteStream);
                TOW receivedPacket = (TOW) objectStream.readObject();

                // Update client status table
                clientStatus.put(receivedPacket.getIdentifier(), receivedPacket);

                System.out.println("Updated client status: " + receivedPacket);

                // Send cluster status back to all active clients
                sendClusterStatusToAll();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendClusterStatusToAll() {
        try {
            long currentTime = System.currentTimeMillis();
            Iterator<Map.Entry<Integer, TOW>> iterator = clientStatus.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Integer, TOW> entry = iterator.next();
                TOW client = entry.getValue();
                
                // Remove clients that haven't sent updates in 30 seconds
                if (currentTime - client.getTimestamp() > TIMEOUT) {
                    iterator.remove();
                    System.out.println("Client " + client.getIdentifier() + " marked as DEAD.");
                }
            }

            // Send updated status to all clients
            for (TOW client : clientStatus.values()) {
                String clusterStatus = "Current Active Clients: " + clientStatus.values();
                
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
                objectStream.writeObject(clusterStatus);
                objectStream.flush();
                byte[] data = byteStream.toByteArray();

                DatagramPacket statusPacket = new DatagramPacket(data, data.length, client.getSenderIP(), client.getDestPort());
                socket.send(statusPacket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws SocketException {
        UDPServer server = new UDPServer();
        server.listenForClients();
    }
}
