import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Peer {
    private DatagramSocket socket;
    private int clientIdentifier;
    private static SecureRandom random = new SecureRandom();
    private final int MAX_PEERS = 6;
    private final long TIMEOUT = 30000; // 30 seconds timeout
    private boolean[] clientIsAlive = new boolean[MAX_PEERS];
    private InetAddress[] clientAddresses = new InetAddress[MAX_PEERS];
    private int[] clientPorts = new int[MAX_PEERS];
    private long[] lastContact = new long[MAX_PEERS];
    private long[] lastPacketTime = new long[MAX_PEERS];
    private final ExecutorService threadPool; // Thread pool for managing sending and receiving
    private ArrayList<String> clientFiles = new ArrayList<>();
    private ArrayList<ArrayList<String>> allFiles = new ArrayList<ArrayList<String>>();

    public Peer(int id) throws IOException {
        this.clientIdentifier = id;
        this.clientAddresses = null;
        this.socket = new DatagramSocket();
        for(int i = 0; i < MAX_PEERS; i++){
            lastContact[i] = System.currentTimeMillis();
            clientIsAlive[i] = true;
        }
        this.threadPool = Executors.newFixedThreadPool(MAX_PEERS); // Thread pool with a number of threads of the max peers - for instance, 5 means 5 threads for handling other peers and a 6th for sending packets

        // Initialize client status
        for (int i = 0; i < MAX_PEERS; i++) {
            clientIsAlive[i] = (i == id - 1);
        }
    }

    public void start() {
        // Start sending packets in a separate thread
        threadPool.submit(() -> {
            while (true) {
                try {
                    // Check if any given peer is dead
                    for(int i = 0; i < MAX_PEERS; i++){
                        if (System.currentTimeMillis() - lastContact[i] > TIMEOUT) {
                            if (clientIsAlive[i]) {
                                System.out.println("Client " + (i + 1) + " marked as DEAD. Will continue sending packets until client comes online.");
                                clientIsAlive[i] = false;
                            }
                        } else if (!clientIsAlive[i]) {
                            System.out.println("Client " + (i + 1) + " is BACK ONLINE.");
                            clientIsAlive[i] = true;
                        }
                    }

                    // Wait from 0-30 seconds before sending a new packet
                    int waitTime = random.nextInt(30000);
                    System.out.println("Client " + clientIdentifier + " waiting for " + waitTime / 1000 + " seconds...");
                    Thread.sleep(waitTime);

                    // Reads in the currently existing file names
                    clientFiles = getFileNames(new File("C:/CSC340 Project Files"));

                    for(int i = 0; i < MAX_PEERS - 1; i++){
                        if(clientAddresses[i] == (InetAddress.getLocalHost())){
                            //don't send a packet to yourself
                        }else{
                            // Create a TOW packet
                            TOW packet = new TOW(clientIdentifier, clientAddresses[i], clientPorts[i], "I am alive!", clientFiles);
                            // Serialize TOW object
                            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
                            objectOutputStream.writeObject(packet);
                            objectOutputStream.flush();
                            byte[] sendData = byteOutputStream.toByteArray();

                            // Send the packet
                            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, packet.getDestIP(), packet.getDestPort());
                            socket.send(sendPacket);
                            System.out.println("TOW packet sent to peer " + (i + 1) + ".");
                        }
                        
                    }
        

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
                
         try {
            byte[] incomingData = new byte[4096];
            DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
            socket.receive(incomingPacket);

            // Hand the packet off to a worker thread 
            threadPool.submit(() -> handlePeerPacket(incomingPacket));

        } catch (IOException e) {
a            e.printStackTrace();
        }

        // Start listening for responses in a separate thread
        /* 
        threadPool.submit(() -> {
            while (true) {
                try {
                    byte[] incomingData = new byte[4096];
                    DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                    
                    // Set timeout to avoid waiting forever
                    socket.setSoTimeout((int) TIMEOUT * 5); // Waits longer than the normal timeout time, to avoid going offline if peers are having momentary issues
                    socket.receive(incomingPacket);

                    // Deserialize the TOW packet
                    ByteArrayInputStream byteInputStream = new ByteArrayInputStream(incomingPacket.getData(), 0, incomingPacket.getLength());
                    ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
                    TOW receivedPacket = (TOW) objectInputStream.readObject();
                    int ID = receivedPacket.getIdentifier() - 1;

                    allFiles.get(ID).clear();
                    allFiles.get(ID).addAll(receivedPacket.getClientFiles());

                    if(!clientIsAlive[ID]){
                        System.out.println("Good news! Peer " + receivedPacket.getIdentifier() + " is alive.");
                        clientIsAlive[ID] = true;
                    }
                    lastContact[receivedPacket.getIdentifier() - 1] = receivedPacket.getTimestamp();

                    System.out.println(receivedPacket.getString());

                    // Print client statuses
                    System.out.println("Status of other clients:");
                    for (int i = 0; i < MAX_PEERS; i++) {
                        if(clientIsAlive[i]){
                            System.out.println("Peer " + (i + 1) + " is ALIVE");
                        }else{
                            System.out.println("Peer " + (i + 1) + " is DEAD");
                        }
                    }

                    // Print all files
                    for(int i = 0; i < MAX_PEERS; i++){
                        System.out.print("Peer " + (i + 1) + " files: ");
                        for(int j = 0; j < allFiles.get(i).size(); j++){
                            System.out.print(allFiles.get(i).get(j) + ", ");
                        }
                        System.out.println();
                    }

                } catch (SocketTimeoutException e) {
                    // If no response from peers in TIMEOUT duration, mark all as dead
                    for(int i = 0; i < MAX_PEERS; i++){
                        if (System.currentTimeMillis() - lastContact[i] > TIMEOUT) {
                            if (clientIsAlive[i]) {
                                System.out.println("Peer marked as DEAD. Continuing to send packets...");
                                clientIsAlive[i] = false;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        */
        
    }

    private synchronized void handlePeerPacket(DatagramPacket packet) {
        try {
            // Deserialize the received TOW object
            ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
            ObjectInputStream objectStream = new ObjectInputStream(byteStream);
            TOW receivedPacket = (TOW) objectStream.readObject();

            int id = receivedPacket.getIdentifier() - 1; // Converts the identifier to array index

            // Ensure only valid client IDs are processed - don't want unknown clients potentially sending in malicious packets
            if (id >= 0 && id < MAX_PEERS) {
                // Check if the packet is the most recent one
                if (receivedPacket.getTimestamp() > lastPacketTime[id]) {
                    lastPacketTime[id] = receivedPacket.getTimestamp();
                    clientIsAlive[id] = true;
                    lastContact[id] = System.currentTimeMillis();
                    System.out.println("Good news! Client " + (id + 1) + " is alive.");

                    // set the file list's files by replacing the old file listing with the new one
                    allFiles.get(id).clear();
                    allFiles.get(id).addAll(receivedPacket.getClientFiles());
                    System.out.println("Client " + (id + 1) + " currently has files: ");
                    for(int i = 0; i < allFiles.get(id).size(); i++){
                        System.out.print(allFiles.get(id).get(i) + ", ");
                    }
                    System.out.println();

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

    private ArrayList<String> getFileNames(File directory) {
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
        int clientId = random.nextInt(6) + 1; // Generate a random client ID (1-6) - this will be replaced later down the line
        Client client = new Client(clientId);
        client.start(); // Start sending and listening
    }
}