import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class Tracker {
    private static final int BUFFER_SIZE = 1024;
    private static final int TRACKER_PORT = 12345;

    private List<PeerInfo> peers;

    public Tracker() {
        this.peers = new ArrayList<>();
    }

    public void start() {
        try {
            DatagramSocket socket = new DatagramSocket(TRACKER_PORT);
            System.out.println("Tracker started on port " + TRACKER_PORT);
    
            while (true) {
                byte[] receiveData = new byte[BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
    
                String message = new String(receivePacket.getData()).trim();
                InetAddress peerAddress = receivePacket.getAddress();
                int peerPort = receivePacket.getPort();
    
                String[] parts = message.split(":");
                String command = parts[0];
    
                if (command.equals("REGISTER")) {
                    String username = parts[1];
                    PeerInfo peerInfo = new PeerInfo(peerAddress, peerPort, username);
                    peers.add(peerInfo);
                    System.out.println("Peer registered: " + peerAddress.getHostAddress() + ":" + peerPort);
                    sendPeerList(socket, peerAddress, peerPort);
                } else if (command.equals("UNREGISTER")) {
                    removePeer(peerAddress, peerPort);
                    System.out.println("Peer unregistered: " + peerAddress.getHostAddress() + ":" + peerPort);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    private void sendPeerList(DatagramSocket socket, InetAddress address, int port) throws IOException {
        StringBuilder peerListBuilder = new StringBuilder();
        for (PeerInfo peer : peers) {
            String peerEntry = peer.getAddress().getHostAddress() + "," + peer.getPort();
            peerListBuilder.append(peerEntry).append(":");
        }

        if (peerListBuilder.length() > 0) {
            peerListBuilder.deleteCharAt(peerListBuilder.length() - 1);
        }

        String peerListMessage = peerListBuilder.toString();
        byte[] sendData = peerListMessage.getBytes();

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
        socket.send(sendPacket);
    }

    private void removePeer(InetAddress address, int port) {
        PeerInfo peerToRemove = null;
        for (PeerInfo peer : peers) {
            if (peer.getAddress().equals(address) && peer.getPort() == port) {
                peerToRemove = peer;
                break;
            }
        }
        if (peerToRemove != null) {
            peers.remove(peerToRemove);
        }
    }

    public static void main(String[] args) {
        Tracker tracker = new Tracker();
        tracker.start();
    }

    private static class PeerInfo {
        private InetAddress address;
        private int port;
        private String username;

        public PeerInfo(InetAddress address, int port, String username) {
            this.address = address;
            this.port = port;
            this.username = username;
        }

        public InetAddress getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }

        public String getUsername() {
            return username;
        }
    }
}
