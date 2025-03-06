import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Server {
    private DatagramSocket socket;
    private final ExecutorService threadPool;
    private final long TIMEOUT = 30000; // 30 seconds timeout
    private final int MAX_CLIENTS = 6; // Maximum number of clients
    private boolean[] clientIsAlive = new boolean[MAX_CLIENTS]; // Tracks which clients are alive
    private long[] lastSeen = new long[MAX_CLIENTS]; // Tracks last time each client was seen
    private long[] lastPacketTime = new long[MAX_CLIENTS]; // Tracks last packet timestamp per client
    public InetAddress[] IPs = new InetAddress[MAX_CLIENTS]; // Tracks client IPs

    public Server(int port, int threadPoolSize) throws SocketException, UnknownHostException {
        socket = new DatagramSocket(port);
        threadPool = Executors.newFixedThreadPool(threadPoolSize);
        // Initially, all clients are marked as dead
        for (int i = 0; i < MAX_CLIENTS; i++) {
            clientIsAlive[i] = false;
            lastSeen[i] = System.currentTimeMillis();
            lastPacketTime[i] = 0; // Ensures packets are always "newer"
        }
    }

    public void start() {
        System.out.println("Server started. Listening for clients...");

        while (true) {
            try {
                byte[] incomingData = new byte[4096];
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                byte[] j = incomingPacket.getData();
                socket.receive(incomingPacket);



                // Hand the packet off to a worker thread using a lambda
                threadPool.submit(() -> handleClientPacket(incomingPacket));

            } catch (IOException e) {
                e.printStackTrace();
            }
            checkClientStatus();
        }
    }

    private synchronized void handleClientPacket(DatagramPacket packet) {
        try {
            // Deserialize the received TOW object
            ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
            ObjectInputStream objectStream = new ObjectInputStream(byteStream);
            TOW receivedPacket = (TOW) objectStream.readObject();

            int id = receivedPacket.getIdentifier() - 1; // Converts the identifier to array index

            // Ensure only valid client IDs are processed - don't want unknown clients potentially sending in malicious packets
            if (id >= 0 && id < MAX_CLIENTS) {
                // Check if the packet is the most recent one
                if (receivedPacket.getTimestamp() > lastPacketTime[id]) {
                    lastPacketTime[id] = receivedPacket.getTimestamp();
                    clientIsAlive[id] = true;
                    lastSeen[id] = System.currentTimeMillis();
                    System.out.println("Good news! Client " + (id + 1) + " is alive.");

                    // Send acknowledgment response to the client
                    sendAcknowledgment(packet.getAddress(), packet.getPort());

                } else {
                    // Ignore any outdated packets
                    System.out.println("Received an outdated packet from Client " + (id + 1) + ". Ignoring.");
                }
            } else {
                System.out.println("Detected incoming packet from invalid client number (" + (id + 1) + "). Ignoring.");
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void sendAcknowledgment(InetAddress clientIP, int clientPort) {
        try {
            TOW responsePacket = new TOW(clientIP, clientPort, clientIsAlive);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ObjectOutputStream obj = new ObjectOutputStream(bytes);
            obj.writeObject(responsePacket);
            obj.flush();
            byte[] responseData = bytes.toByteArray();

            DatagramPacket response = new DatagramPacket(responseData, responseData.length, clientIP, clientPort);
            socket.send(response);
            System.out.println("Acknowledgment sent to Client at " + clientIP + ":" + clientPort);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void checkClientStatus() {
        long currentTime = System.currentTimeMillis();
        for (int i = 0; i < MAX_CLIENTS; i++) {
            if (clientIsAlive[i] && (currentTime - lastSeen[i] > TIMEOUT)) {
                clientIsAlive[i] = false;
                System.out.println("Client " + (i + 1) + " marked as DEAD due to timeout.");
            }
        }
    }

    public static void main(String[] args) throws SocketException, UnknownHostException {
        Server server = new Server(9876, 10);
        server.start();
    }
}