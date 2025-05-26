package ds.namingnote.Multicast;
import ds.namingnote.Config.NNConf;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class MulticastSender implements Runnable {

    public Boolean run = true;
    public String name;
    public MulticastSender(String name){

        this.name = name ;
    }

    @Override
    public void run() {

        while(!Thread.currentThread().isInterrupted()) {

            String message = NNConf.PREFIX + "{" + name + "}";
            byte[] buffer = message.getBytes();

            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress group = InetAddress.getByName(NNConf.MULTICAST_GROUP);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, NNConf.Multicast_PORT);
                socket.send(packet);
                System.out.println("Multicast message sent: " + message);
            } catch (UnknownHostException e) {
                System.err.println("Unknown host: " + NNConf.MULTICAST_GROUP);
            } catch (IOException e) {
                System.err.println("IO Exception during multicast send: " + e.getMessage());
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
                System.out.println("Multicast thread interrupted.");
                break; // Exit the loop
            }
        }
        System.out.println("MulticastSender stopped");

    }

    public void setRun(Boolean run) {
        this.run = run;
    }

}