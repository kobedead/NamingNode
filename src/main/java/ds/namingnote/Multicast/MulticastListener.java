package ds.namingnote.Multicast;

import ds.namingnote.Config.NNConf;
import ds.namingnote.Service.NodeService;
import ds.namingnote.Utilities.Utilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.*;
import java.io.*;
import java.util.regex.*;


public class MulticastListener implements Runnable {


    private MulticastSocket socket;
    private InetAddress group;

    private NodeService nodeService;

    public MulticastListener(NodeService nodeService) {
        this.nodeService = nodeService;


        try {
            // Create the multicast socket
            socket = new MulticastSocket(NNConf.Multicast_PORT);

            // Create a multicast group address
            group = InetAddress.getByName(NNConf.MULTICAST_GROUP);

            SocketAddress sockaddr = new InetSocketAddress(group, NNConf.Multicast_PORT);

            // Join the multicast group
            socket.joinGroup(sockaddr, null);                   //can give error

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];  // Buffer for receiving messages
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        System.out.println("Listening for multicast messages on group: " + NNConf.MULTICAST_GROUP + " and port: " + NNConf.Multicast_PORT);

        while (nodeService.isRunning()) {

            try {
                // Receive the incoming packet
                socket.receive(packet);

                // Filter based on the source IP, port, and content of the message
                InetAddress sourceAddress = packet.getAddress();
                if (sourceAddress == InetAddress.getLocalHost() )
                    return;
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

                    //if message has prefix we need to process it
                    String name = extractName(message);                 //check

                    if (name != null && Utilities.mapHash(name) != nodeService.getCurrentNode().getID()){ //otherwise it picks up its own multicast

                        nodeService.processIncomingMulticast(sourceAddress.toString().replace("/", "") , Utilities.mapHash(name));
                    }
                    else
                        System.out.println("Name not found in multicast message or own multicast received");
                } else {
                    System.out.println("Filtered out message with incorrect prefix: " + message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        System.out.println("MulticastSender stopped");

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


        // Check if the pattern matches
        if (matcher.find()) {
            // Return the first captured group (the name inside the braces)
            return matcher.group(1);
        }

        // Return null or empty string if no match is found
        return null;
    }

}
