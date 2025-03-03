import java.io.*;
import java.net.*;
import java.util.Random;

public class UDPClient {
    DatagramSocket socket;
    InetAddress serverAddress;
    int serverPort = 9876;
    int clientId;
    
    public UDPClient(int clientId) throws UnknownHostException {
        this.clientId = clientId;
        this.serverAddress = InetAddress.getByName("localhost");
    }

    public void sendAvailability() {
        try {
            socket = new DatagramSocket();
            
            // Simulate file listing
            //String[] files = {"file1.txt", "file2.log", "image.png"};

            // Create and serialize a TOW object
            TOW packet = new TOW(clientId, serverAddress, serverPort, "file1, file2, file3");
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
            objectStream.writeObject(packet);
            objectStream.flush();
            byte[] sendData = byteStream.toByteArray();

            // Send UDP packet to server
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
            socket.send(sendPacket);
            System.out.println("Client " + clientId + " sent availability update.");

            // Receive cluster status update from server
            byte[] incomingData = new byte[4096];
            DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
            socket.receive(incomingPacket);
            
            ByteArrayInputStream byteInStream = new ByteArrayInputStream(incomingPacket.getData(), 0, incomingPacket.getLength());
            ObjectInputStream objectInputStream = new ObjectInputStream(byteInStream);
            String serverResponse = (String) objectInputStream.readObject();
            System.out.println("Client " + clientId + " received update: " + serverResponse);

            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws InterruptedException, UnknownHostException {
        Random rand = new Random();
        UDPClient client = new UDPClient(1);

        while (true) {
            client.sendAvailability();
            Thread.sleep(rand.nextInt(30000)); // Wait random 0-30 seconds before next update
        }
    }
}
