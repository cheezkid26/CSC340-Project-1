import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private DatagramSocket socket;
    private final ExecutorService threadPool;
    private final long TIMEOUT = 30000; // 30 seconds until marked dead
    private final int MAX_CLIENTS = 6; // maximum clients is 6, but this being a variable allows it to be modified to suit any user's needs
    private boolean[] clientIsAlive = new boolean[MAX_CLIENTS]; // keeps track of which clients are alive and which are dead
    private long[] lastSeen = new long[MAX_CLIENTS]; // keeps track of when each client was last heard from
    private long[] lastPacketTime = new long[MAX_CLIENTS]; // keeps track of when the last packet from each client was sent
    public InetAddress[] IPs = new InetAddress[MAX_CLIENTS]; // keeps track of client IPs

    public Server(int port, int threadPoolSize) throws SocketException {
        socket = new DatagramSocket(port);
        threadPool = Executors.newFixedThreadPool(threadPoolSize);

        // at first, treat all clients as dead until you hear from them
        for (int i = 0; i < MAX_CLIENTS; i++) {
            clientIsAlive[i] = false;
            lastSeen[i] = 0;
            lastPacketTime[i] = 0;
        }
    }

    public void start() {
        System.out.println("Server started, listening for clients...");

        while (true) {
            try {
                byte[] incomingData = new byte[2048];
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                socket.receive(incomingPacket);

                // hands the packet off to a thread ("hey, my cousin can handle your request, i've gotta keep listening")
                threadPool.execute(new ClientHandler(incomingPacket, this));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Updates client status 
    public synchronized void updateClientStatus(TOW packet) {
        int id = packet.getIdentifier() - 1;
        
        // this just makes sure no unrecognized clients can send packets 
        if(id >= 0 && id < MAX_CLIENTS){
            clientIsAlive[id] = true;
            lastSeen[id] = System.currentTimeMillis();
            System.out.println("Good news! Client " + (id + 1) + " is alive.");
        } else {
            System.out.println("Detected incoming packet from invalid client number (" + (id + 1) + "). Ignoring packet.");
        }
    }

    public void checkClientStatus() {
        long currentTime = System.currentTimeMillis();
        for (int i = 0; i < MAX_CLIENTS; i++) {
            if (clientIsAlive[i] && (currentTime - lastSeen[i] > TIMEOUT)) {
                clientIsAlive[i] = false; // client hasn't been seen in over 30 seconds, mark as dead
                System.out.println("Client " + i + " marked as DEAD due to timeout.");
            }
        }
    }

    // Sends the updated client status to all clients
    public void sendStatusToAll() {
        
        
        
        try {
            StringBuilder statusMessage = new StringBuilder("Current Active Clients: ");
            for (int i = 0; i < MAX_CLIENTS; i++) {
                if (clientIsAlive[i]) {
                    statusMessage.append("Client ").append(i).append(" is alive. ");
                }
            }

            byte[] data = statusMessage.toString().getBytes();

            for (int i = 0; i < MAX_CLIENTS; i++) {
                if (clientIsAlive[i]) {
                    InetAddress clientIP = InetAddress.getByName("localhost"); // Modify for actual client IP
                    int clientPort = 9876; // Modify for actual client port

                    DatagramPacket statusPacket = new DatagramPacket(data, data.length, clientIP, clientPort);
                    socket.send(statusPacket);
                }
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
    
                // update client status only if the packet is the most recent
                if(receivedPacket.getTimestamp() > lastPacketTime[receivedPacket.getIdentifier() - 1]){
                    server.updateClientStatus(receivedPacket);

                    // Send acknowledgment back to the client
                    //String response = "Server received your update.";
                
                    TOW responsePacket = new TOW(9876, InetAddress.getByName("localhost"), 5000, server.clientIsAlive);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    ObjectOutputStream obj = new ObjectOutputStream(bytes);
                    obj.writeObject(responsePacket);
                    obj.flush();
                    byte[] responseData = bytes.toByteArray();

                    DatagramPacket response = new DatagramPacket(responseData, responseData.length, packet.getAddress(), packet.getPort());
                    server.socket.send(response);
                } else {
                    String badPacket = "Received an outdated packet. Ignoring.";
                    byte[] bp = badPacket.getBytes();
                    DatagramPacket badResponse = new DatagramPacket(bp, bp.length, receivedPacket.getSenderIP(), receivedPacket.getSenderPort());
                }
    
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
    
}
