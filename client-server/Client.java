import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Scanner;
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
    private ArrayList<String> clientFiles = new ArrayList<>();
    private ArrayList<ArrayList<String>> allFiles = new ArrayList<ArrayList<String>>();

    public Client(int id) throws IOException {
        this.clientIdentifier = id;
        this.serverAddress = InetAddress.getByName("localhost");// InetAddress.getByName("localhost");
        this.socket = new DatagramSocket(9875);
        this.lastContact = System.currentTimeMillis();
        this.serverIsDead = false;
        this.threadPool = Executors.newFixedThreadPool(2); // Thread pool with 2 threads
        this.allFiles = new ArrayList<ArrayList<String>>();

        // Initialize client status
        for (int i = 0; i < MAX_CLIENTS; i++) {
            clientIsAlive[i] = false;
        }

        for(int i = 0; i < MAX_CLIENTS; i++){
            allFiles.add(new ArrayList<String>());
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

                    // Reads in the currently existing file names
                    clientFiles = getFileNames();

                    // Create a TOW packet
                    TOW packet = new TOW(clientIdentifier, serverAddress, 9876, "I am alive!", clientFiles);
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

                    // Deserialize the TOW packet
                    ByteArrayInputStream byteInputStream = new ByteArrayInputStream(incomingPacket.getData(), 0, incomingPacket.getLength());
                    ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
                    TOW receivedPacket = (TOW) objectInputStream.readObject();

                    ArrayList<ArrayList<String>> tempFiles = receivedPacket.getAllFiles();
                    boolean[] tempIsAlive = receivedPacket.getClientStatuses();
                    lastContact = receivedPacket.getTimestamp();

                    if(tempFiles != null){
                        allFiles = tempFiles;
                    }
                    if(tempIsAlive != null){
                        clientIsAlive = tempIsAlive;
                    }

                    System.out.println(receivedPacket.getString());

                    // Print client statuses
                    System.out.println("Status of other clients:");
                    for (int i = 0; i < MAX_CLIENTS; i++) {
                        if(clientIsAlive[i]){
                            System.out.println("Client " + (i + 1) + " is ALIVE");
                        }else{
                            System.out.println("Client " + (i + 1) + " is DEAD");
                        }
                    }

                    // Print all files
                    for(int i = 0; i < MAX_CLIENTS; i++){
                        System.out.print("Client " + (i + 1) + " files: ");
                        for(int j = 0; j < allFiles.get(i).size(); j++){
                            System.out.print(allFiles.get(i).get(j) + ", ");
                        }
                        System.out.println();
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

    // Reads in file names
    private ArrayList<String> getFileNames() {
        File directory = new File(System.getProperty("user.dir") + File.separator + "home"); 
        ArrayList<String> fileNames = new ArrayList<>();
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileNames.add(file.getName());
                    }
                }
            }
        }
        return fileNames;
    }


    public static void main(String[] args) throws IOException {
        Scanner input = new Scanner(System.in);
        System.out.println("Which client is this? (Whole numbers only.)");
        int clientID = input.nextInt();
        Client client = new Client(clientID);
        input.close();
        client.start(); // Start sending and listening
    }
}