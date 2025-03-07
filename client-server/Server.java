import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.ArrayList;

public class Server {
    private DatagramSocket socket;
    private final ExecutorService threadPool;
    private final long TIMEOUT = 30000; // 30 seconds timeout
    private final int MAX_CLIENTS = 6; // Maximum number of clients
    private boolean[] clientIsAlive = new boolean[MAX_CLIENTS]; // Tracks which clients are alive
    private long[] lastSeen = new long[MAX_CLIENTS]; // Tracks last time each client was seen
    private long[] lastPacketTime = new long[MAX_CLIENTS]; // Tracks last packet timestamp per client
    private InetAddress[] IPs = new InetAddress[MAX_CLIENTS]; // Tracks client IPs
    private int[] ports = new int[MAX_CLIENTS]; // Tracks ports
    private ArrayList<ArrayList<String>> allFiles = new ArrayList<ArrayList<String>>(); // Stores the file listing

    public Server(int port, int threadPoolSize) throws SocketException, UnknownHostException {
        socket = new DatagramSocket(port);
        threadPool = Executors.newFixedThreadPool(threadPoolSize);
        // Initially, all clients are marked as dead
        for (int i = 0; i < MAX_CLIENTS; i++) {
            clientIsAlive[i] = false;
            lastSeen[i] = System.currentTimeMillis();
            lastPacketTime[i] = 0; // Ensures the first arriving packets are always considered the newest ones
            loadClientAddresses("addresses.txt");
        }

        for(int i = 0; i < MAX_CLIENTS; i++){
            allFiles.add(new ArrayList<String>());
        }
    }

    public void start() {
        System.out.println("Server started. Listening for clients...");

        while (true) {
            try {

                byte[] incomingData = new byte[4096];
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                socket.receive(incomingPacket);

                // Hand the packet off to a thread ("hey, my cousin will help you out here, i need to keep listening for other clients")
                threadPool.submit(() -> handleClientPacket(incomingPacket));
            } catch (IOException e) {
                e.printStackTrace();
            }
            checkClientStatus();
        }
    }

    private synchronized void handleClientPacket(DatagramPacket packet) {
        try {
            // Deserialize the received TOW
            ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
            ObjectInputStream objectStream = new ObjectInputStream(byteStream);
            TOW receivedPacket = (TOW) objectStream.readObject();

            int id = receivedPacket.getIdentifier() - 1; // Converts the identifier to array index

            // Ensure only valid client IDs are processed - don't want unknown clients potentially sending in malicious packets
            if (id >= -1 && id < MAX_CLIENTS) {
                // Check if the packet is the most recent one
                if (receivedPacket.getTimestamp() > lastPacketTime[id]) {
                    lastPacketTime[id] = receivedPacket.getTimestamp();
                    lastSeen[id] = System.currentTimeMillis();

                    if(!clientIsAlive[id]){
                        System.out.println("Good news! Client " + (id + 1) + " is back alive.");
                        clientIsAlive[id] = true;    
                    }

                    // set the file list's files by replacing the old file listing with the new one
                    allFiles.get(id).clear();
                    allFiles.get(id).addAll(receivedPacket.getClientFiles());
                    System.out.println("Client " + (id + 1) + " currently has files: ");
                    for(int i = 0; i < allFiles.get(id).size(); i++){
                        System.out.print(allFiles.get(id).get(i) + ", ");
                    }
                    System.out.println();

                    // Send update to all clients
                    sendStatusUpdate();
                    System.out.println("Updated server and file listing sent to clients.");    
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

    private void sendStatusUpdate() {
        try {
            for(int i = 0; i < MAX_CLIENTS; i++){
                TOW responsePacket = new TOW(IPs[i], ports[i], clientIsAlive, allFiles);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream obj = new ObjectOutputStream(bytes);
                obj.writeObject(responsePacket);
                obj.flush();
                byte[] responseData = bytes.toByteArray();

                DatagramPacket response = new DatagramPacket(responseData, responseData.length, IPs[i], ports[i]);
                socket.send(response);
            }
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

    private void loadClientAddresses(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int i = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+"); // Split by space or tab
                if (parts.length == 2) {
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    IPs[i] = InetAddress.getByName(ip);
                    ports[i] = port; 
                    i++;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading peer config file: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws SocketException, UnknownHostException {
        Server server = new Server(9876, 10);
        server.start();
    }
}