import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.concurrent.*;

public class Client {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int clientIdentifier;
    private static SecureRandom random = new SecureRandom();
    private final int MAX_CLIENTS = 6;
    private final long TIMEOUT = 30000; // 30 seconds timeout
    private boolean[] clientIsAlive = new boolean[MAX_CLIENTS];
    private boolean serverIsDead;
    private long lastContact;
    private final ExecutorService threadPool; // Thread pool for managing sending and receiving

    public Client(int id) throws IOException {
        this.clientIdentifier = id;
        this.serverAddress = InetAddress.getByName("localhost");
        this.socket = new DatagramSocket();
        this.lastContact = System.currentTimeMillis();
        this.serverIsDead = false;
        this.threadPool = Executors.newFixedThreadPool(2); // Thread pool with 2 threads

        // Initialize client status
        for (int i = 0; i < MAX_CLIENTS; i++) {
            clientIsAlive[i] = (i == id - 1);
        }
    }

    public void start() {
        // Start sending packets in a separate thread
        threadPool.submit(() -> {
            while (true) {
                try {
                    // Check if the server is dead
                    if (System.currentTimeMillis() - lastContact > TIMEOUT) {
                        if (!serverIsDead) {
                            System.out.println("Server marked as DEAD. Will continue sending packets until server comes online.");
                            serverIsDead = true;
                        }
                    } else if (serverIsDead) {
                        System.out.println("Server is BACK ONLINE.");
                        serverIsDead = false;
                    }

                    // Wait from 0-30 seconds before sending a new packet
                    int waitTime = random.nextInt(30000);
                    System.out.println("Client " + clientIdentifier + " waiting for " + waitTime / 1000 + " seconds...");
                    Thread.sleep(waitTime);

                    // Create a TOW packet
                    //TOW packet = new TOW(clientIdentifier, serverAddress, 9876, "I am alive!");
                    TOW packet = new TOW(clientIdentifier, serverAddress, 9876, "I am alive!");
                    // Serialize TOW object
                    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
                    objectOutputStream.writeObject(packet);
                    objectOutputStream.flush();
                    byte[] sendData = byteOutputStream.toByteArray();

                    // Send the packet
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 9876);
                    socket.send(sendPacket);
                    System.out.println("TOW packet sent from client.");
                    System.out.println("Serialized packet size: " + sendData.length);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // Start listening for responses in a separate thread
        threadPool.submit(() -> {
            while (true) {
                try {
                    byte[] incomingData = new byte[4096]; 
                    DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                    
                    // Set timeout to avoid waiting forever
                    socket.setSoTimeout((int) TIMEOUT * 5); // Waits longer than the normal timeout time, to avoid going offline if the server is having momentary issues
                    socket.receive(incomingPacket);

                    // Deserialize the TOW object
                    ByteArrayInputStream byteInputStream = new ByteArrayInputStream(incomingPacket.getData(), 0, incomingPacket.getLength());
                    ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
                    TOW receivedPacket = (TOW) objectInputStream.readObject();

                    clientIsAlive = receivedPacket.getClientStatuses();
                    lastContact = receivedPacket.getTimestamp();

                    System.out.println(receivedPacket.getData());
                    // Print client statuses
                    System.out.println("Status of other clients:");
                    for (int i = 0; i < MAX_CLIENTS; i++) {
                        System.out.println("Client " + (i + 1) + " is " + (clientIsAlive[i] ? "ALIVE" : "DEAD"));
                    }

                } catch (SocketTimeoutException e) {
                    // If no response from server in TIMEOUT duration, mark server as dead
                    if (System.currentTimeMillis() - lastContact > TIMEOUT) {
                        if (!serverIsDead) {
                            System.out.println("Server marked as DEAD. Continuing to send packets...");
                            serverIsDead = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void main(String[] args) throws IOException {
        int clientId = random.nextInt(6) + 1; // Generate a random client ID (1-6) - this will be replaced later down the line
        Client client = new Client(clientId);
        client.start(); // Start sending and listening
    }
}