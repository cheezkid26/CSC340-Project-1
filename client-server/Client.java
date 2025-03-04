import java.io.*;
import java.net.*;
import java.security.SecureRandom;

public class Client {
    DatagramSocket socket;
    private int clientIdentifier;
    private static SecureRandom random = new SecureRandom();

    public Client(int id) {
        this.clientIdentifier = id;
    }

    public void createAndListenSocket() {
        try {
            socket = new DatagramSocket();
            InetAddress serverAddress = InetAddress.getByName("localhost");
            
            int numPacketsSent = 0;

            while(true){
                int waitTime = random.nextInt(30001);
                System.out.println("Client " + clientIdentifier + " waiting for " + waitTime/1000 + " seconds...");
                Thread.sleep(waitTime);
                // Create a TOW packet
                TOW packet = new TOW(clientIdentifier, serverAddress, 9876, "I am alive!", numPacketsSent);

                // Serialize TOW object so it can be sent through DatagramPacket
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
                objectStream.writeObject(packet);
                objectStream.flush();
                byte[] sendData = byteStream.toByteArray();

                // Send the packet
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 9876);
                socket.send(sendPacket);
                System.out.println("TOW packet sent from client.");

                // Receive response from the server
                byte[] incomingData = new byte[1024];
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                socket.receive(incomingPacket);
                String response = new String(incomingPacket.getData(), 0, incomingPacket.getLength()).trim();
                System.out.println("Response from server: " + response);

                //socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Client client = new Client(random.nextInt(10));
        client.createAndListenSocket();
    }
}
