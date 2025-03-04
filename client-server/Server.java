import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private DatagramSocket socket;
    private Map<Integer, TOW> clientStatus = new ConcurrentHashMap<>();
    private final ExecutorService threadPool;
    private final long TIMEOUT = 30000; // 30 seconds timeout

    public Server(int port, int threadPoolSize) throws SocketException {
        socket = new DatagramSocket(port);
        threadPool = Executors.newFixedThreadPool(threadPoolSize);
    }

    public void start() {
        System.out.println("Server started, listening for clients...");

        while (true) {
            try {
                byte[] incomingData = new byte[2048];
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                socket.receive(incomingPacket);

                // Hand off to a worker thread
                threadPool.execute(new ClientHandler(incomingPacket, this));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Updates client status (thread-safe)
    public synchronized void updateClientStatus(TOW packet) {
        clientStatus.put(packet.getIdentifier(), packet);
        System.out.println("Updated client status: " + packet);
    }

    // Sends the updated client status to all clients
    public void sendStatusToAll() {
        try {
            long currentTime = System.currentTimeMillis();
            Iterator<Map.Entry<Integer, TOW>> iterator = clientStatus.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Integer, TOW> entry = iterator.next();
                TOW client = entry.getValue();

                if (currentTime - client.getTimestamp() > TIMEOUT) {
                    iterator.remove();
                    System.out.println("Client " + client.getIdentifier() + " marked as DEAD.");
                }
            }

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
        Server server = new Server(9876, 10); // 10 threads
        server.start();
    }

    private class ClientHandler implements Runnable {
        private DatagramPacket packet;
        private Server server;
    
        public ClientHandler(DatagramPacket packet, Server server) {
            this.packet = packet;
            this.server = server;
        }
    
        @Override
        public void run() {
            try {
                // Deserialize the received TOW object
                ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                ObjectInputStream objectStream = new ObjectInputStream(byteStream);
                TOW receivedPacket = (TOW) objectStream.readObject();
    
                // Update client status
                server.updateClientStatus(receivedPacket);
    
                // Send acknowledgment back to the client
                String response = "Server received your update.";
                byte[] responseData = response.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, packet.getAddress(), packet.getPort());
                server.socket.send(responsePacket);
    
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
    
}
