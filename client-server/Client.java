import java.io.*;
import java.net.*;

public class Client {
    DatagramSocket socket;

    public Client() {}

    public void createAndListenSocket() {
        try {
            socket = new DatagramSocket();
            InetAddress serverAddress = InetAddress.getByName("localhost");

            // Create a TOW packet
            TOW packet = new TOW(1, serverAddress, 9876, "Greetings. My name is Beef.");

            // Serialize TOW object
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
            objectStream.writeObject(packet);
            objectStream.flush();
            byte[] sendData = byteStream.toByteArray();

            // Send UDP packet
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 9876);
            socket.send(sendPacket);
            System.out.println("TOW packet sent from client.");

            // Receive response
            byte[] incomingData = new byte[1024];
            DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
            socket.receive(incomingPacket);
            String response = new String(incomingPacket.getData(), 0, incomingPacket.getLength()).trim();
            System.out.println("Response from server: " + response);

            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.createAndListenSocket();
    }
}
