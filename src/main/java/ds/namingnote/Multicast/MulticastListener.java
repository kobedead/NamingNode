package ds.namingnote.Multicast;

import java.io.IOException;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ds.namingnote.Config.NNConf; // Assuming this is where your constants are
import ds.namingnote.Service.NodeService;  //  Assuming this is your NodeService
import ds.namingnote.Utilities.Utilities;

public class MulticastListener implements Runnable {

    private MulticastSocket socket;
    private InetAddress group;
    private NodeService nodeService; //  Use the NodeService

    public MulticastListener(NodeService nodeService) { // Inject NodeService
        this.nodeService = nodeService;
        try {
            socket = new MulticastSocket(NNConf.MULTICAST_PORT);
            group = InetAddress.getByName(NNConf.MULTICAST_GROUP);
            SocketAddress sockaddr = new InetSocketAddress(group, NNConf.MULTICAST_PORT);
            socket.joinGroup(sockaddr, null);
        } catch (IOException e) {
            throw new RuntimeException("Error initializing MulticastListener", e); // More robust error handling
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        System.out.println("Listening for multicast messages on " + NNConf.MULTICAST_GROUP + ":" + NNConf.MULTICAST_PORT);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                processMessage(message, packet.getAddress()); // Pass the sender address
            } catch (IOException e) {
                if (!socket.isClosed()) { // Only print if the socket is not closed.
                    System.err.println("IO Exception during multicast receive: " + e.getMessage());
                }
                break; // Exit loop if there's an error receiving
            }
        }
        System.out.println("Multicast Listener Thread Stopped"); //Add a stop message
        close(); // Ensure socket is closed when the thread finishes
    }

    private void processMessage(String message, InetAddress sourceAddress) {
        if (message.startsWith(NNConf.NODE_PREFIX)) {
            processNodeAnnouncement(message, sourceAddress.toString().replace("/", ""));
        } else if (message.startsWith(NNConf.LOCK_PREFIX)) {
            processLockNotification(message);
        } else {
            System.out.println("Filtered out message with unknown prefix: " + message);
        }
    }

    private void processNodeAnnouncement(String message, String sourceAddress) {
        String name = extractName(message);
        if (name != null && Utilities.mapHash(name) != nodeService.getCurrentNode().getID()) {
            nodeService.processIncomingMulticast(sourceAddress, name);
        } else {
            System.out.println("Name not found or self in multicast message: " + message);
        }
    }

    private void processLockNotification(String message) {
        String data = message.substring(NNConf.LOCK_PREFIX.length());
        String[] parts = data.split(":");
        if (parts.length == 3) {
            String filename = parts[0];
            boolean isLocked = Boolean.parseBoolean(parts[1]);
            // Assuming you have a method in NodeService (or a dedicated service) to handle lock updates
            nodeService.updateFileLockStatus(filename, isLocked);
        } else {
            System.err.println("Invalid lock notification format: " + message);
        }
    }



    private String extractName(String input) {
        String regex = "\\{([^}]+)\\}";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.leaveGroup(group);
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing multicast socket: " + e.getMessage());
        }
    }
}
