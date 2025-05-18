package ds.namingnote.Multicast;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import ds.namingnote.Config.NNConf; // Assuming this is where your constants are

public class MulticastSender implements Runnable {

    private String name;
    private boolean running = true; // Use a boolean for the running state

    public MulticastSender(String name) {
        this.name = name;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) { // Use try-with-resources
            InetAddress group = InetAddress.getByName(NNConf.MULTICAST_GROUP);

            while (running) { // Use the running boolean
                sendNodeAnnouncement(socket, group); // Send announcement
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                    System.err.println("Multicast Sender Thread Interrupted");
                    break; // Exit the loop
                }
            }
            System.out.println("Multicast Sender Thread Stopped"); //Add a stop message
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + NNConf.MULTICAST_GROUP);
        } catch (IOException e) {
            System.err.println("IO Exception during multicast send: " + e.getMessage());
        }
        // No need to close the socket explicitly with try-with-resources
    }

    private void sendNodeAnnouncement(DatagramSocket socket, InetAddress group) throws IOException {
        String message = NNConf.NODE_PREFIX + "{" + name + "}";  // Corrected variable name
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, NNConf.MULTICAST_PORT);
        socket.send(packet);
        System.out.println("Multicast Node Announcement sent: " + message);
    }

    public void sendLockNotification(String filename, boolean isLocked) {
        String message = NNConf.LOCK_PREFIX + filename + ":" + isLocked;
        byte[] buffer = message.getBytes();
        try (DatagramSocket socket = new DatagramSocket()) { // Use a new socket for sending lock notifications
            InetAddress group = InetAddress.getByName(NNConf.MULTICAST_GROUP);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, NNConf.MULTICAST_PORT);
            socket.send(packet);
            System.out.println("Multicast Lock Notification sent: " + message);
        } catch (IOException e) {
            System.err.println("IO Exception during multicast send: " + e.getMessage());
        }
    }

    public void stopRunning() {
        this.running = false; // Use the boolean to stop the loop
    }
}

