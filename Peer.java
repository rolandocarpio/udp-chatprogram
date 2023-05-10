import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public class Peer {
    private static final int BUFFER_SIZE = 1024;
    private static final int TRACKER_PORT = 12345;

    private String username;
    private InetAddress trackerAddress;
    private int trackerPort;
    private DatagramSocket socket;
    private List<PeerInfo> peers;

    public Peer(String username, InetAddress trackerAddress, int trackerPort) {
        this.username = username;
        this.trackerAddress = trackerAddress;
        this.trackerPort = trackerPort;
        this.peers = new ArrayList<>();
    }

    public void startChat() {
        try {
            // Create a UDP socket for peer-to-peer communication
            socket = new DatagramSocket();

            // Register with the tracker
            registerWithTracker();

            // Start listening for incoming messages in a separate thread
            startListening();

            // Read messages from the user and send them to other peers
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String message = reader.readLine();
                if (message.equalsIgnoreCase("exit")) {
                    break;
                }
                sendMessageToPeers(message);
            }

            // Unregister from the tracker before exiting
            unregisterFromTracker();

            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void registerWithTracker() throws IOException {
        String registerMessage = "REGISTER:" + username;
        byte[] sendData = registerMessage.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, trackerAddress, trackerPort);
        socket.send(sendPacket);

        byte[] receiveData = new byte[BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);

        String peerListMessage = new String(receivePacket.getData()).trim();
        String[] peers = peerListMessage.split(":");
        for (String peer : peers) {
            String[] parts = peer.split(",");
            String address = parts[0];
            int port = Integer.parseInt(parts[1]);
            InetAddress peerAddress = InetAddress.getByName(address);
            PeerInfo peerInfo = new PeerInfo(peerAddress, port);
            this.peers.add(peerInfo);
            System.out.println("Connected to peer: " + address + ":" + port);
        }
    }

    private void unregisterFromTracker() throws IOException {
        String unregisterMessage = "UNREGISTER:" + username;
        byte[] sendData = unregisterMessage.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, trackerAddress, trackerPort);
        socket.send(sendPacket);
    }

    private void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                byte[] receiveData = new byte[BUFFER_SIZE];
                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);
                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                    System.out.println(message);
    
                    // Reset the buffer
                    receiveData = new byte[BUFFER_SIZE];
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        listenerThread.start();
    }
    

    private void sendMessageToPeers(String message) throws IOException {
        String timestampedMessage = "[" + new Date() + "] " + username + ": " + message;
        byte[] sendData = timestampedMessage.getBytes();

        // Send the message to all peers (excluding self)
        for (PeerInfo peer : peers) {
            if (!peer.getAddress().equals(socket.getLocalAddress()) || peer.getPort() != socket.getLocalPort()) {
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, peer.getAddress(), peer.getPort());
                socket.send(sendPacket);
            }
        }
    }

    public static void main(String[] args) {
        try {
            InetAddress trackerAddress = InetAddress.getLocalHost();
            int trackerPort = TRACKER_PORT;

            // Get the username from the user
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your username: ");
            String username = scanner.nextLine();

            // Create a peer and start the chat
            Peer peer = new Peer(username, trackerAddress, trackerPort);
            peer.startChat();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private static class PeerInfo {
        private InetAddress address;
        private int port;

        public PeerInfo(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }

        public InetAddress getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }
    }
}
