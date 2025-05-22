package ds.namingnote.Multicast;

import ds.namingnote.Config.NNConf;
import ds.namingnote.Service.NodeService;
import ds.namingnote.Utilities.Utilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.*;
import java.io.*;
import java.util.Enumeration;
import java.util.regex.*;


public class MulticastListener implements Runnable {


    private MulticastSocket socket;
    private InetAddress group;

    private NodeService nodeService;

    public MulticastListener(NodeService nodeService) {
        this.nodeService = nodeService;


        try {
            socket = new MulticastSocket(NNConf.Multicast_PORT);
            group = InetAddress.getByName(NNConf.MULTICAST_GROUP);

            // --- MODIFICATION: Specify Network Interface ---
            NetworkInterface netIf = null;
            if (nodeService.getCurrentNode() != null) {
                String currentNodeIp = nodeService.getCurrentNode().getIP();
                try {
                    netIf = findNetworkInterfaceByIp(currentNodeIp);
                    if (netIf != null) {
                        System.out.println("Listener attempting to join multicast on interface: " + netIf.getDisplayName() + " for IP: " + currentNodeIp);
                    } else {
                        System.err.println("Listener could not find a specific network interface for IP: " + currentNodeIp + ". Will try default.");
                    }
                } catch (SocketException e) {
                    System.err.println("SocketException while finding network interface for " + currentNodeIp + ": " + e.getMessage());
                }
            } else {
                // Fallback if currentNode is not yet available (should ideally not happen when listener starts)
                System.err.println("Listener: CurrentNode in NodeService is null, cannot determine specific IP. Will try default interface.");
            }

            socket.joinGroup(new InetSocketAddress(group, NNConf.Multicast_PORT), netIf); // Pass netIf (can be null for OS default)
            // --- END MODIFICATION ---

            System.out.println("Successfully joined multicast group: " + group + " on port " + NNConf.Multicast_PORT);

        } catch (IOException e) {
            System.err.println("IOException in MulticastListener setup: " + e.getMessage());
            e.printStackTrace(); // Crucial for seeing the error
        }
    }

    // Helper method to add to MulticastListener or a utility class
    private NetworkInterface findNetworkInterfaceByIp(String ip) throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface ni = networkInterfaces.nextElement();
            Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress inetAddress = inetAddresses.nextElement();
                if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().equals(ip)) {
                    if (ni.supportsMulticast() && ni.isUp()) {
                        return ni;
                    }
                }
            }
        }
        // Fallback: if specific IP not found, try to find *any* suitable non-loopback multicast interface
        // This might be too broad, but can be a last resort.
        networkInterfaces = NetworkInterface.getNetworkInterfaces(); // Re-get
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface ni = networkInterfaces.nextElement();
            if (ni.supportsMulticast() && !ni.isLoopbackAddress() && ni.isUp()) {
                System.out.println("Warning: Specific IP " + ip + " not matched. Falling back to first available multicast interface: " + ni.getDisplayName());
                return ni; // Use with caution
            }
        }
        return null;
    }


    @Override
    public void run() {
        byte[] buffer = new byte[1024];  // Buffer for receiving messages
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        System.out.println("Listening for multicast messages on group: " + NNConf.MULTICAST_GROUP + " and port: " + NNConf.Multicast_PORT);

        while (!Thread.currentThread().isInterrupted()) {

            System.out.println("mult");
            try {
                // Receive the incoming packet
                socket.receive(packet);

                // Filter based on the source IP, port, and content of the message
                InetAddress sourceAddress = packet.getAddress();
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("multicast message received : " + message + "from " + sourceAddress);

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }


                // Check if the message contains the specific keyword
                if (message.startsWith(NNConf.PREFIX)) {
                    System.out.println("Multicast 1");

                    //if message has prefix we need to process it
                    String name = extractName(message);                 //check
                    System.out.println("Multicast 2");

                    if (name != null && Utilities.mapHash(name) != nodeService.getCurrentNode().getID()){ //otherwise it picks up its own multicast
                        System.out.println("Multicast 3");

                        nodeService.processIncomingMulticast(sourceAddress.toString().replace("/", "") , name);
                    }
                    else
                        System.out.println("Name not found in multicast message");
                } else {
                    System.out.println("Filtered out message with incorrect prefix: " + message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    // Close the socket when done
    public void close() {
        try {
            socket.leaveGroup(group);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static String extractName(String input) {
        // Regular expression to match text inside curly braces
        String regex = "\\{([^}]+)\\}";  // Match content inside {}

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        System.out.println("Multicast 4");

        // Check if the pattern matches
        if (matcher.find()) {
            // Return the first captured group (the name inside the braces)
            return matcher.group(1);
        }

        // Return null or empty string if no match is found
        return null;
    }

}
