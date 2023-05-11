import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

//combined a javaswing gui
public class Tracker extends JFrame {
    private static final int BUFFER_SIZE = 1024;
    private static final int TRACKER_PORT = 12345;

    private List<PeerInfo> peers;
    private JTextArea logTextArea;

    public Tracker() {
        super("Tracker");

        peers = new ArrayList<>();

        logTextArea = new JTextArea(20, 40);
        logTextArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logTextArea);

        // add components to the jframe
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(scrollPane, BorderLayout.CENTER);
        setContentPane(contentPane);

        pack();
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public void startTracker() {
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    DatagramSocket socket = new DatagramSocket(TRACKER_PORT);
                    publish("Tracker started on port " + TRACKER_PORT);
                    InetAddress localhost = InetAddress.getLocalHost();
                    String ipAddress = localhost.getHostAddress();
                    publish("IP Address: " + ipAddress);

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
                            publish("Peer registered: " + peerAddress.getHostAddress() + ":" + peerPort);
                            sendPeerList(socket, peerAddress, peerPort);
                        } else if (command.equals("UNREGISTER")) {
                            removePeer(peerAddress, peerPort);
                            publish("Peer unregistered: " + peerAddress.getHostAddress() + ":" + peerPort);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    logTextArea.append(message + "\n");
                }
            }
        };

        worker.execute();
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

        // allows UDP sending asynchronously without blocking the GUI
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
                socket.send(sendPacket);
                return null;
            }
        };

        worker.execute();
    }

    private void removePeer(InetAddress address, int port) {
        Iterator<PeerInfo> iterator = peers.iterator();
        while (iterator.hasNext()) {
            PeerInfo peer = iterator.next();
            if (peer.getAddress().equals(address) && peer.getPort() == port) {
                iterator.remove();
                break;
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Tracker tracker = new Tracker();
                tracker.setVisible(true);
                tracker.startTracker();
            }
        });
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