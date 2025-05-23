package ds.namingnote.Service;

import ds.namingnote.Agents.FailureAgent;
import ds.namingnote.Agents.SyncAgent;
import ds.namingnote.Config.NNConf;
import ds.namingnote.Multicast.MulticastListener;
import ds.namingnote.Multicast.MulticastSender;
import ds.namingnote.Utilities.NextAndPreviousNodeDTO;
import ds.namingnote.Utilities.Node;
import ds.namingnote.Utilities.Utilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Service
public class NodeService {

    Node currentNode = null;
    Node nextNode = null;
    Node previousNode = null;


    private boolean namingServerResponse = false;

    private Thread multicastSenderThread;
    private Thread multicastListenerThread;

    private boolean listenerStarted = false;

    @Autowired
    private ReplicationService replicationService;

    private final Semaphore startSignal = new Semaphore(0); // initially blocked
    boolean running = false;

    public void waitForStartSignal() throws InterruptedException {
        System.out.println("Waiting for start signal...");
        startSignal.acquire(); // blocks until released
    }

    public void startProcessing() {
        if (running) {
            System.out.println("Node is already running");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Node is already running");
        }

        running = true;
        startSignal.release(); // now it's safe to unblock
        System.out.println("Start signal received for node");
    }


    /**
     *  Method setNameBegin
     *  Gets called from NamingNoteApplication if startup and name is set.
     *  This method is used as makeshift constructor.
     *
     * @param name name given tot the node
     */
    public void setNameBegin(String name) throws UnknownHostException {

        //get own ip
        InetAddress localHost = InetAddress.getLocalHost(); //get own IP
        //create node object for current node
        Node currentnode = new Node(Utilities.mapHash(name) , localHost.getHostAddress());
        //set current node
        this.currentNode =  currentnode ;
        System.out.println("Current node is set: " + currentnode.getIP());

        //create the threads for the multicast
        multicastSenderThread = new Thread(new MulticastSender(name));
        multicastListenerThread = new Thread(new MulticastListener(this));

        //begin sending messages
        multicastSenderThread.start();
    }

    /**
     * Method checkConnection
     * called when other nodes and/or naming server gets multicast
     * check if the node has successfully joint the network
     *
     */
    public void checkConnection(){

        System.out.println("CheckConnect called ");

        // if node is connected -> stop sending and start listening
        if(!listenerStarted && namingServerResponse && nextNode != null && previousNode != null) {

            listenerStarted = true;
            System.out.println("Multicast stops");
            multicastSenderThread.interrupt(); //stop the sending thread
            multicastListenerThread.start();  //start the listening thread
            replicationService.start();  //start the replication phase
            return;
        }

        if(namingServerResponse)
            System.out.println("Got message from server");
        if(nextNode != null)
            System.out.println("got message from other node : Next updated");
        if(previousNode != null)
            System.out.println("got message from other node : Previous updated");

    }


    /**
     * Method processIncomingMulticast
     * Gets called from the multicastListener thread
     * process the multicast received from other node
     *
     *
     * @param ip ip from sender of multicast
     * @param name name of node (sender multicast)
     */
    public void processIncomingMulticast(String ip, String name){
        int nameHash = Utilities.mapHash(name);
        Node incommingNode = new Node(nameHash , ip);



        //new node is the only one with me on network
        if (currentNode.getID() == nextNode.getID() && currentNode.getID() == previousNode.getID()){

            //now there are 2 node, so they both need to set their neighbors to each other.

            //set previous and next of other node

            setOtherNextNode(ip, currentNode, name);
            setOtherPreviousNode(ip, currentNode, name);

            //set previous and next of this node
            nextNode = incommingNode;
            previousNode = incommingNode;

            System.out.println("Node : " + currentNode.getID() + " .Multicast Processed, 2 Nodes On Network");
            replicationService.start();
            return;

        //only 2 nodes are present in a loop
        }else if (nextNode.getID() == previousNode.getID()) {
            System.out.println("Only 2 node present, third wants to join");
            //node is at top end of loop
            if (nextNode.getID() < currentNode.getID()){
                System.out.println("Node is at the top end of the loop");
                //new node is largest in the network
                if (incommingNode.getID() > currentNode.getID()) {
                    setNextNode(incommingNode);
                    setOtherPreviousNode(ip, currentNode, name); //set new node
                }
                //new node sits in between the 2 nodes
                else if (incommingNode.getID() > previousNode.getID()) {
                    setPreviousNode(incommingNode);
                    setOtherNextNode(ip, currentNode, name);
                }
                //new node is smallest in the network
                else if (incommingNode.getID() < previousNode.getID()){
                    setNextNode(incommingNode);
                    setOtherPreviousNode(ip,currentNode,name);
                }
            //node is at bottom end of loop
            }else if (nextNode.getID() > currentNode.getID()) {
                System.out.println("Node is a the bottom end of the loop");
                //new node is smallest in network
                if (incommingNode.getID() < currentNode.getID()){
                    setPreviousNode(incommingNode);
                    setOtherNextNode(ip ,currentNode,name);
                }
                //new node is biggest in network
                else if (incommingNode.getID() > nextNode.getID()){
                    setPreviousNode(incommingNode);
                    setOtherNextNode(ip ,currentNode ,name);
                }
                //new node sits in between the 2 nodes
                else if (incommingNode.getID() < nextNode.getID()) {
                    setNextNode(incommingNode);
                    setOtherPreviousNode(ip, currentNode, name);
                }

            //more than 2 nodes are present on the network
            }else{
                System.out.println("More than 2 node present");
                //new node needs to be seeded in between existing nodes
                if (incommingNode.getID() > currentNode.getID() && incommingNode.getID() < nextNode.getID()){
                    setNextNode(incommingNode);
                    setOtherPreviousNode(ip , currentNode , name);
                } else if (incommingNode.getID() < currentNode.getID() && incommingNode.getID() > previousNode.getID()) {
                    setPreviousNode(incommingNode);
                    setOtherNextNode(ip, currentNode, name);
                }
                //check edge cases where new node is smallest or biggest on the network

                //this node is the smallest or biggest node (when the ring wraps around)
                if (previousNode.getID() > nextNode.getID()) {
                    //new node is biggest in network
                    if (incommingNode.getID() > previousNode.getID()) {
                        setPreviousNode(incommingNode);
                        setOtherNextNode(ip, currentNode, name);
                    }
                    //new node is smallest in network
                    else if (incommingNode.getID() < currentNode.getID()) {
                        setNextNode(incommingNode);
                        setOtherPreviousNode(ip, currentNode, name);
                    }
                    //new node sits in between currentNode and nextNode (wrapping around)
                    else if (incommingNode.getID() > currentNode.getID()) {
                        setNextNode(incommingNode);
                        setOtherPreviousNode(ip, currentNode, name);
                    }
                    //new node sits in between previousNode and currentNode (wrapping around)
                    else if (incommingNode.getID() < previousNode.getID()) {
                        setPreviousNode(incommingNode);
                        setOtherNextNode(ip, currentNode, name);
                    }

                } else {
                    // Standard case where the ring is not wrapping around based on IDs
                    // New node is the smallest
                    if (incommingNode.getID() < currentNode.getID() && incommingNode.getID() < previousNode.getID()) {
                        setPreviousNode(incommingNode);
                        setOtherNextNode(ip, currentNode, name);
                    }
                    // New node is the largest
                    else if (incommingNode.getID() > currentNode.getID() && incommingNode.getID() > nextNode.getID()) {
                        setNextNode(incommingNode);
                        setOtherPreviousNode(ip, currentNode, name);
                    }
                }

            }

            System.out.println("Node " + currentNode.getID() + ": Processed multicast from " + name + "(" + incommingNode.getID() + ")");
            System.out.println("  My Next: " + (nextNode != null ? nextNode.getID() : "null"));
            System.out.println("  My Previous: " + (previousNode != null ? previousNode.getID() : "null"));
            replicationService.start();

        }

    }



    public ResponseEntity<String> setOtherNextNode(String ip , Node node, String name){

        String mapping = "/node/id/next";
        String uri = "http://"+ip+":"+ NNConf.NAMINGNODE_PORT +mapping;

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON); // Indicate that we are sending JSON
        HttpEntity<Node> requestEntity = new HttpEntity<>(node, headers);

        System.out.println("setOtherNextID for node" + name + " on ip " + ip);
        System.out.println("Call to " + uri);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.POST, requestEntity, String.class);

            System.out.println("setOtherNextID : " +response.getBody());  //we need to check for error ig

            return  response;                                  //check
        } catch (Exception e) {
            System.out.println("Exception in communication between nodes " + e.getMessage() + " -> handleFailure");
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    public ResponseEntity<String> setOtherPreviousNode(String ip , Node node , String name){

        String mapping = "/node/id/previous";
        String uri = "http://"+ip+":"+NNConf.NAMINGNODE_PORT+mapping;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON); // Indicate that we are sending JSON
        HttpEntity<Node> requestEntity = new HttpEntity<>(node, headers);

        System.out.println("setOtherPreviousID for node" + name + " on ip " + ip);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.POST, requestEntity, String.class);

            System.out.println("setOtherPreviousID : " +response.getBody());  //we need to check for error ig

            return  response;                                  //check
        } catch (Exception e) {
            // If communication between nodes fails, execute failure
            System.out.println("Exception in communication between nodes " + e.getMessage() + " -> handleFailure");
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }





    public void calculatePreviousAndNext(int numberOfNodes) {

        namingServerResponse = true;
        System.out.println("NamingServer has responded, number of nodes : "+ numberOfNodes);

        if (numberOfNodes == -1){
         //hash of node already in map of namingserver -> cant join
            System.out.println("hash of node already in map of namingserver -> cant join ==> SHUTDOWN");
            System.exit(0);
        }
        else if (numberOfNodes == 1) {
            /// This is the only node in the network
            previousNode = currentNode;
            nextNode = currentNode;

        } else {
            /// There are other nodes in this network
            ///  The node should receive parameters for its next and previous node
            ///  Other nodes should send this after receiving the Multicast
            ///  This node expects a call on its REST endpoints to set the previous and next node.

        }
        this.checkConnection();
    }


    @Scheduled(fixedRate = 30000) // Runs every 30 seconds
    public void pingNextAndPreviousNode() {
        if (running) {
            System.out.println("Pinging previous and next nodes...");

            pingNode(previousNode, "previous");
            pingNode(nextNode, "next");
        }
    }

    private void pingNode(Node node, String label) {
        if (Objects.equals(node, null)) {
            System.out.println(label + " Node is null, skipping ping.");
            return;
        }

        String url = "http://" + node.getIP() + ":" + NNConf.NAMINGNODE_PORT + "/node/ping";
        RestTemplate restTemplate = new RestTemplate();

        try {
            String response = restTemplate.getForObject(url, String.class);
            System.out.println("Ping to " + label + " node (" + node.getID() + ") , ip : "+ node.getID() +" successful: " + response);
        } catch (Exception e) {
            System.err.println("Failed to ping " + label + " node (" + node.getID() +  ") , ip : "+ node.getID() +" successful: " + e.getMessage());
            handleFailure(node);
        }
    }

    public void handleFailure(Node failedNode) {
        String baseUri = "http://" + NNConf.NAMINGSERVER_HOST + ":" + NNConf.NAMINGSERVER_PORT + "/namingserver";
        RestTemplate restTemplate = new RestTemplate();

        try {
            //get the failed node next and previous nodes from naming server
            String getUri = baseUri + "/node/nextAndPrevious/" + failedNode.getID();
            ResponseEntity<Map> response = restTemplate.getForEntity(getUri, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, String> stringMap = response.getBody();

                if (stringMap == null) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "NextAndPrevious set returned null");
                }

                Map<Integer, String> nextAndPrevious = stringMap.entrySet()
                        .stream()
                        .collect(Collectors.toMap(
                                entry -> Integer.parseInt(entry.getKey()), // Convert key to Integer
                                Map.Entry::getValue
                        ));
                System.out.println("Next and Previous for node " + failedNode.getIP() + ": " + nextAndPrevious);
                //the nextAndPrevious map will always contain this nodes entry as one of the 2

                if (nextAndPrevious.keySet().size() == 1) { // Happens if previous == next -> im the only one on the network
                    //im the only other node on the network then IG
                    setNextNode(currentNode);
                    setPreviousNode(currentNode);
                }
                else if (failedNode == previousNode){
                    //if failed node is previous -> its previous becomes our previous <-> we become the next of its previous

                    //we only need the previous node of the failed node (next is this node)
                    Map.Entry<Integer, String> previousEntry = nextAndPrevious.entrySet().stream().min(Map.Entry.comparingByKey()).orElse(null);
                    Node failedPreviousNode = new Node(previousEntry.getKey() , previousEntry.getValue());

                    setPreviousNode(failedPreviousNode);
                    setOtherNextNode(failedPreviousNode.getIP() , currentNode , failedPreviousNode.getIP());

                    //create the failed agent and forward this
                    FailureAgent failureAgent = new FailureAgent(failedNode , failedPreviousNode , currentNode);
                    forwardAgent(failureAgent , nextNode);


                }else if (failedNode == nextNode){
                    //if failed node is next -> its next becomes our next <-> we become the previous of its next

                    //we only need the next node of failed node (previous is this node)
                    Map.Entry<Integer, String> nextEntry = nextAndPrevious.entrySet().stream().max(Map.Entry.comparingByKey()).orElse(null);
                    Node failedNextNode = new Node(nextEntry.getKey() , nextEntry.getValue());

                    setNextNode(failedNextNode);
                    setOtherPreviousNode(failedNextNode.getIP() , currentNode , failedNextNode.getIP());

                    //create the failed agent and forward this
                    FailureAgent failureAgent = new FailureAgent(failedNode , failedNextNode , currentNode);
                    forwardAgent(failureAgent , nextNode);

                }

            } else {
                System.out.println("Failed to retrieve next and previous info for node: " + failedNode.getIP());
            }
        } catch (Exception e) {
            System.err.println("Error fetching next and previous info: " + e.getMessage());
        }
        //if successfully set everything we can delete the failed node from the naming server
        try {
            String deleteUri = baseUri + "/node/by-id/" + failedNode.getID();
            restTemplate.delete(deleteUri);
            System.out.println("Node " + failedNode.getID() + " removed successfully.");
        } catch (Exception e) {
            System.err.println("Error deleting node: " + e.getMessage());
        }
    }




    public void shutdown() {
        if (running) {
            //before remove node out of network -> file transfer
            replicationService.shutdown();

            //remove node from network
            setOtherPreviousNode(nextNode.getIP(), nextNode , "Set Other Next");
            setOtherNextNode(previousNode.getIP(), previousNode , "Set other previous");

            String mapping = "/namingserver" + "/node/by-id/" + currentNode.getID();
            String deleteUri = "http://" + NNConf.NAMINGSERVER_HOST + ":" + NNConf.NAMINGSERVER_PORT + mapping ;

            RestTemplate restTemplate = new RestTemplate();
            restTemplate.delete(deleteUri);
            running = false;
            System.out.println("Shutdown requested. Going back to waiting state...");
        } else {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Node is already shut down");
        }
    }


    public void forwardAgent(Serializable agent, Node targetNode) {
        if (targetNode == null || targetNode.getID() == currentNode.getID()) {
            System.out.println("Cannot forward agent, target is null or self.");
            return;
        }
        String url = "http://" + targetNode.getIP() + ":" + NNConf.NAMINGNODE_PORT + "/agent/execute";
        System.out.println("Forwarding agent of type " + agent.getClass().getSimpleName() + " to: " + url);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(agent);
            byte[] serializedAgent = bos.toByteArray();

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(serializedAgent, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Agent successfully forwarded to " + targetNode.getIP() + ". Response: " + response.getBody());
            } else {
                System.out.println("Failed to forward agent to " + targetNode.getIP() + ". Status: " + response.getStatusCode() + ", Body: " + response.getBody());
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }






    public Node getPreviousNode () {
        return previousNode;
    }
    public Node getNextNode () {
        return nextNode;
    }

    public void setPreviousNode(Node previousNode) {
        this.previousNode = previousNode;
        System.out.println("Previous ID set to " + previousNode.getID() + " with IP: " + previousNode.getIP());
        this.checkConnection();
    }

    public void setNextNode(Node nextNode) {
        this.nextNode = nextNode;
        System.out.println("Next ID set to " + nextNode.getID() + " with IP: " + nextNode.getIP());
        this.checkConnection();
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    public boolean isRunning() {
        return running;
    }

    public NextAndPreviousNodeDTO getNextAndPrevious() {
        Node next = this.getNextNode();
        Node previous = this.getPreviousNode();
        if (next != null && previous != null ) {
            return new NextAndPreviousNodeDTO(next.getID(), previous.getID());
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Next or previous node is null, is this the only node in the network?");
        }
    }
}