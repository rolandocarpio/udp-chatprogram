import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Peer extends JFrame {
    private static final int BUFFER_SIZE = 1024;
    private static final int TRACKER_PORT = 12345;

    private String username;
    private DatagramSocket socket;
    private List<PeerInfo> peers;

    private JTextArea chatArea;
    private JTextField messageField;

    public Peer(String username, InetAddress trackerAddress, int trackerPort) {
        this.username = username;
        this.peers = new ArrayList<>();

        setTitle(username + "'s Peer Chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 800);
        setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();

        class SendButtonAction implements ActionListener {
            @Override
            public void actionPerformed(ActionEvent e) {
                String message = messageField.getText().trim();
                if (!message.isEmpty()) {
                    try {
                        if (message.equals(".")) {
                            disconnectAndNotifyPeers(trackerAddress, trackerPort);
                        } else {
                            sendMessageToPeers(message);
                        }
                        messageField.setText("");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        JButton sendButton = new JButton("Send");
        SendButtonAction sendButtonAction = new SendButtonAction();
        sendButton.addActionListener(sendButtonAction);
        messageField.addActionListener(sendButtonAction);

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);
    }

    private void disconnectAndNotifyPeers(InetAddress trackerAddress, int trackerPort) throws IOException {
        String disconnectMessage = "DISCONNECTED:" + username;
        byte[] sendData = disconnectMessage.getBytes();

        // Send the disconnection message to all peers (excluding self)
        for (PeerInfo peer : peers) {
            if (!peer.getAddress().equals(socket.getLocalAddress()) || peer.getPort() != socket.getLocalPort()) {
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, peer.getAddress(),
                        peer.getPort());
                socket.send(sendPacket);
            }
        }

        // Unregister from the tracker
        unregisterFromTracker(trackerAddress, trackerPort);

        // Close the socket and exit the program
        disconnect();
        System.exit(0);
    }

    private void disconnect() {
        socket.close();
    }

    public void startChat(InetAddress trackerAddress, int trackerPort) {
        try {
            // Create a UDP socket for peer-to-peer communication
            socket = new DatagramSocket();

            // Register with the tracker
            registerWithTracker(trackerAddress, trackerPort);

            // Start listening for incoming messages in a separate thread
            startListening();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void registerWithTracker(InetAddress trackerAddress, int trackerPort) throws IOException {
        // Create a UDP socket for peer-to-peer communication
        socket = new DatagramSocket();
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
            chatArea.append("Connected to peer: " + address + ":" + port + "\n");
        }

        // Send the updated peer list to all peers (including the newly registered peer)
        for (PeerInfo peer : this.peers) {
            sendPeerList(peer.getAddress(), peer.getPort());
        }
    }

    private void sendPeerList(InetAddress address, int port) throws IOException {
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

    private void unregisterFromTracker(InetAddress trackerAddress, int trackerPort) throws IOException {
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
                    chatArea.append(message + "\n");

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
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, peer.getAddress(),
                        peer.getPort());
                socket.send(sendPacket);
            }
        }
    }

    public static void main(String[] args) {
        try {
            // Get the username from the user
            String username = JOptionPane.showInputDialog("Enter your username");

            InetAddress trackerAddress = InetAddress.getLocalHost();
            int trackerPort = TRACKER_PORT;

            // Create a peer and start the chat
            Peer peer = new Peer(username, trackerAddress, trackerPort);
            peer.setVisible(true);
            peer.startChat(trackerAddress, trackerPort);
        } catch (IOException e) {
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