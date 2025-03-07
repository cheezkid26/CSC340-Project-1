import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.Scanner;

public class Peer {
    private DatagramSocket socket;
    private int peerIdentifier;
    private static SecureRandom random = new SecureRandom();
    private final int MAX_PEERS = 6;
    private final long TIMEOUT = 35000; // 35 seconds timeout
    private boolean[] peerIsAlive = new boolean[MAX_PEERS];
    private InetAddress[] peerAddresses = new InetAddress[MAX_PEERS];
    private int[] peerPorts = new int[MAX_PEERS];
    private long[] lastContact = new long[MAX_PEERS];
    private long[] lastPacketTime = new long[MAX_PEERS];
    private final ExecutorService threadPool; // Thread pool for managing sending and receiving
    private ArrayList<String> peerFiles = new ArrayList<>();
    private ArrayList<ArrayList<String>> allFiles = new ArrayList<ArrayList<String>>();

    public Peer(int id) throws IOException {
        this.peerIdentifier = id;
        loadPeerAddresses("addresses.txt");
        this.socket = new DatagramSocket(peerPorts[id - 1]);
        for(int i = 0; i < MAX_PEERS; i++){ //assume peers start as dead, set lastContact to 0
            lastContact[i] = 0;
            peerIsAlive[i] = false;
        }
        peerIsAlive[id - 1] = true; //sets itself to consider itself alive
        this.threadPool = Executors.newFixedThreadPool(MAX_PEERS); // Thread pool with a number of threads of the max peers - for instance, 5 means 5 threads for handling other peers and a 6th for sending packet

        // Initializes allFiles properly
        for(int i = 0; i < MAX_PEERS; i++){
            allFiles.add(new ArrayList<String>());
        }
    }

    public void start() {
        // Start sending packets in a separate thread
        threadPool.submit(() -> {
            while (true) {
                try {
                    lastContact[peerIdentifier - 1] = System.currentTimeMillis(); // not very elegant, but prevents peer from marking itself as dead
                    // Check if any given peer is dead
                    for(int i = 0; i < MAX_PEERS; i++){
                        if (System.currentTimeMillis() - lastContact[i] > TIMEOUT) {
                            if (peerIsAlive[i]) {
                                System.out.println("Peer " + (i + 1) + " marked as DEAD. Will continue sending packets until peer comes online.");
                                peerIsAlive[i] = false;
                            }
                        }
                    }

                    // Wait from 0-30 seconds before sending a new packet
                    int waitTime = random.nextInt(30000);
                    System.out.println("Peer " + peerIdentifier + " waiting for " + (waitTime / 1000) + " seconds...");
                    Thread.sleep(waitTime);

                    // Reads in the currently existing file names
                    peerFiles = getFileNames();

                    for(int i = 0; i < MAX_PEERS; i++){
                        if((i + 1) == peerIdentifier){
                            //don't send a packet to yourself
                        }else{
                            // Create a TOW packet
                            TOW packet = new TOW(peerIdentifier, peerAddresses[i], peerPorts[i], "I am alive!", peerFiles);
                            // Serialize TOW object
                            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
                            objectOutputStream.writeObject(packet);
                            objectOutputStream.flush();
                            byte[] sendData = byteOutputStream.toByteArray();

                            // Send the packet
                            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, peerAddresses[i], peerPorts[i]);
                            socket.send(sendPacket);
                            System.out.println("TOW packet sent to peer " + (i + 1) + ".");
                        }
                        
                    }

                    for(int i = 0; i < MAX_PEERS; i++){
                        if(peerIsAlive[i]){
                            System.out.println("Peer " + (i + 1) + " (alive) has files: ");
                            for(int j = 0; j < allFiles.get(i).size(); j++){
                                System.out.print(allFiles.get(i).get(j) + ", ");
                            }
                            System.out.println();
                        }else{
                            System.out.println("Peer " + (i + 1) + " (dead)'s last known files: ");
                            for(int j = 0; j < allFiles.get(i).size(); j++){
                                System.out.print(allFiles.get(i).get(j) + ", ");
                            }
                            System.out.println();
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
            e.printStackTrace();
        }
    }

    private synchronized void handlePeerPacket(DatagramPacket packet) {
        try {
            // Deserialize the received TOW object
            ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
            ObjectInputStream objectStream = new ObjectInputStream(byteStream);
            TOW receivedPacket = (TOW) objectStream.readObject();

            int id = receivedPacket.getIdentifier() - 1; // Converts the identifier to a proper array index

            // Ensure only valid peer IDs are processed - don't want unknown peers potentially sending in malicious packets
            if (id >= 0 && id < MAX_PEERS) {
                // Check if the packet is the most recent one
                if (receivedPacket.getTimestamp() > lastPacketTime[id]) {
                    lastPacketTime[id] = receivedPacket.getTimestamp();
                    lastContact[id] = System.currentTimeMillis();
                    if(!peerIsAlive[id]){
                        System.out.println("Good news! Peer " + (id + 1) + " is alive again.");
                        peerIsAlive[id] = true;
                    }else{
                        System.out.println("Received a packet from peer " + peerIdentifier);
                    }

                    // set the file list's files by replacing the old file listing with the new one
                    allFiles.get(id).clear();
                    allFiles.get(id).addAll(receivedPacket.getClientFiles());
                    System.out.println("Peer " + (id + 1) + " currently has files: ");
                    for(int i = 0; i < allFiles.get(id).size(); i++){
                        System.out.print(allFiles.get(id).get(i) + ", ");
                    }
                    System.out.println();

                } else {
                    // Ignore any outdated packets
                    System.out.println("Received an outdated packet from peer " + (id + 1) + ". Ignoring.");
                }
            } else {
                // Ignore invalid peer numbers
                System.out.println("Detected incoming packet from invalid peer number (" + (id + 1) + "). Ignoring.");
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
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

    private void loadPeerAddresses(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int i = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+"); // Split by space or tab
                if (parts.length == 2) {
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    peerAddresses[i] = InetAddress.getByName(ip);
                    peerPorts[i] = port; 
                    i++;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading peer config file: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        Scanner input = new Scanner(System.in);
        System.out.println("Which peer is this? (Whole numbers only.)");
        int peerID = input.nextInt();
        Peer peer = new Peer(peerID);
        input.close();
        peer.start(); // Start sending and listening
    }
}