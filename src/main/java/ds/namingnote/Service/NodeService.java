package ds.namingnote.Service;

import ds.namingnote.Agents.FailureAgent;
import ds.namingnote.Agents.SyncAgent;
import ds.namingnote.Config.NNConf;
import ds.namingnote.Multicast.MulticastListener;
import ds.namingnote.Multicast.MulticastSender;
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
import java.util.logging.Level;
import java.util.stream.Collectors;

@Service
public class NodeService {

    Node currentNode = null;
    Node nextNode = null;
    Node previousNode = null;

    private boolean biggest;

    private boolean namingServerResponse = false;

    private Thread multicastSenderThread;
    private Thread multicastListenerThread;

    private boolean listenerStarted = false;

    @Autowired
    private ReplicationService replicationService;




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
        int incomingNodeId = Utilities.mapHash(name);
        Node incomingNode = new Node(incomingNodeId, ip);

        // 0. Ignore if it's our own multicast
        if (incomingNode.getID() == currentNode.getID()) {
            return;
        }

        System.out.println("Processing Multicast from: " + name + " (ID: " + incomingNodeId + ") received by Node " + currentNode.getID());
        System.out.println("Current State: My ID=" + currentNode.getID() + ", Prev ID=" + previousNode.getID() + ", Next ID=" + nextNode.getID());

        // Cache current IDs for clarity
        int currentId = currentNode.getID();
        int nextId = nextNode.getID();
        int prevId = previousNode.getID();

        // Case 1: This is the first other node joining (current node was alone).
        // Both nextNode and previousNode point to currentNode itself.
        if (currentId == nextId && currentId == prevId) {
            System.out.println("  Decision: Current node was alone. Connecting to " + incomingNodeId);
            // Current node updates its pointers
            setNextNode(incomingNode);
            setPreviousNode(incomingNode);

            // Tell incomingNode that this currentNode is its next and previous
            setOtherNextNode(incomingNode.getIP(), currentNode, name /* name of incomingNode for log on its side */);
            setOtherPreviousNode(incomingNode.getIP(), currentNode, name);

            replicationService.start(); // Or other post-join actions
            logFinalState(name, incomingNodeId);
            return;
        }

        // General Case: At least two nodes already in the network (including self).
        // We need to determine if `incomingNode` fits between `currentNode` and `nextNode`
        // OR between `previousNode` and `currentNode`.

        // Condition 1: Does incomingNode fit between ME (currentNode) and my NEXT?
        // (currentNode) --> incomingNode --> (nextNode)
        boolean fitsAsMyNext = false;
        if (currentId < nextId) { // Standard case: current ID < next ID (e.g., 10 -> 20)
            if (incomingNodeId > currentId && incomingNodeId < nextId) {
                fitsAsMyNext = true;
            }
        } else { // Wrap-around case: current ID > next ID (e.g., 90 -> 10, current is 'largest')
            if (incomingNodeId > currentId || incomingNodeId < nextId) {
                // incoming is larger than me OR smaller than my next (which is the 'smallest')
                fitsAsMyNext = true;
            }
        }

        if (fitsAsMyNext) {
            System.out.println("  Decision: " + incomingNodeId + " fits as NEW NEXT for " + currentId +
                    " (between " + currentId + " and " + nextId + ")");
            // My old nextNode (nextId) needs to update its previous pointer to incomingNode
            setOtherPreviousNode(nextNode.getIP(), incomingNode, name); // Tell old next about incoming

            // incomingNode's new next is my old nextNode
            setOtherNextNode(incomingNode.getIP(), nextNode, name);
            // incomingNode's new previous is me (currentNode)
            setOtherPreviousNode(incomingNode.getIP(), currentNode, name);

            // My new nextNode is incomingNode
            setNextNode(incomingNode);

            replicationService.start();
            logFinalState(name, incomingNodeId);
            return;
        }

        // Condition 2: Does incomingNode fit between my PREVIOUS and ME (currentNode)?
        // (previousNode) --> incomingNode --> (currentNode)
        boolean fitsAsMyPrevious = false;
        if (prevId < currentId) { // Standard case: previous ID < current ID (e.g., 10 -> 20)
            if (incomingNodeId > prevId && incomingNodeId < currentId) {
                fitsAsMyPrevious = true;
            }
        } else { // Wrap-around case: previous ID > current ID (e.g., 90 -> 10, current is 'smallest')
            if (incomingNodeId > prevId || incomingNodeId < currentId) {
                // incoming is larger than my previous (which is 'largest') OR smaller than me
                fitsAsMyPrevious = true;
            }
        }

        if (fitsAsMyPrevious) {
            System.out.println("  Decision: " + incomingNodeId + " fits as NEW PREVIOUS for " + currentId +
                    " (between " + prevId + " and " + currentId + ")");
            // My old previousNode (prevId) needs to update its next pointer to incomingNode
            setOtherNextNode(previousNode.getIP(), incomingNode, name); // Tell old prev about incoming

            // incomingNode's new previous is my old previousNode
            setOtherPreviousNode(incomingNode.getIP(), previousNode, name);
            // incomingNode's new next is me (currentNode)
            setOtherNextNode(incomingNode.getIP(), currentNode, name);

            // My new previousNode is incomingNode
            setPreviousNode(incomingNode);

            replicationService.start();
            logFinalState(name, incomingNodeId);
            return;
        }

        // If neither of the above, incomingNode is not an immediate neighbor for this currentNode.
        // Another node in the ring will handle it.
        System.out.println("  Decision: " + incomingNodeId + " is not an immediate neighbor for " + currentId + ". No local pointer changes for this incoming node.");
        logFinalState(name, incomingNodeId); // Log state even if no change
    }
    private void logFinalState(String processedNodeName, int processedNodeId) {
        System.out.println("Node " + currentNode.getID() + ": Finished processing multicast from " + processedNodeName + "(" + processedNodeId + ")");
        System.out.println("  My Final Next: " + (nextNode != null ? nextNode.getID() + " (" + nextNode.getIP() + ")" : "null"));
        System.out.println("  My Final Previous: " + (previousNode != null ? previousNode.getID() + " (" + previousNode.getIP() + ")" : "null"));
    }


    public ResponseEntity<String> setOtherBiggest(String ip){

        String mapping = "/node/biggest";
        String uri = "http://"+ip+":"+ NNConf.NAMINGNODE_PORT +mapping;

        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.POST, null, String.class);

            System.out.println("setOtherBiggest : " +response.getBody());  //we need to check for error ig

            return  response;                                  //check
        } catch (Exception e) {
            System.out.println("Exception in communication between nodes " + e.getMessage() + " -> handleFailure");
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }


    }



    public ResponseEntity<String> setOtherNextNode(String ip , Node node, String name){

        String mapping = "/node/id/next";
        String uri = "http://"+ip+":"+ NNConf.NAMINGNODE_PORT +mapping;

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON); // Indicate that we are sending JSON
        HttpEntity<Node> requestEntity = new HttpEntity<>(node, headers);

        System.out.println("setOtherNextID to" + node.getID() + " on ip " + ip);
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

        System.out.println("setOtherPreviousID to " + node.getID() + " on ip " + ip);

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
        System.out.println("Pinging previous and next nodes...");

        pingNode(previousNode, "previous");
        pingNode(nextNode, "next");
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


                    System.out.println("The FAILED node is my previous node  ");
                    System.out.println("Node " + currentNode.getID() );
                    System.out.println("  My Final Next: " + (nextNode != null ? nextNode.getID() + " (" + nextNode.getIP() + ")" : "null"));
                    System.out.println("  My Final Previous: " + (previousNode != null ? previousNode.getID() + " (" + previousNode.getIP() + ")" : "null"));



                    Map.Entry<Integer, String> previousEntry = nextAndPrevious.entrySet().stream().max(Map.Entry.comparingByKey()).orElse(null);
                    Node failedPreviousNode = new Node(previousEntry.getKey() , previousEntry.getValue());

                    System.out.println("FailedPrevNode : " + failedPreviousNode);

                    setOtherNextNode(failedPreviousNode.getIP() , currentNode , failedPreviousNode.getIP());
                    setPreviousNode(failedPreviousNode);

                    //create the failed agent and forward this
                    FailureAgent failureAgent = new FailureAgent(failedNode , failedPreviousNode , currentNode);
                    forwardAgent(failureAgent , nextNode);


                }else if (failedNode == nextNode){
                    //if failed node is next -> its next becomes our next <-> we become the previous of its next

                    System.out.println("The FAILED node is my Next node  ");
                    System.out.println("Node " + currentNode.getID() );
                    System.out.println("  My Final Next: " + (nextNode != null ? nextNode.getID() + " (" + nextNode.getIP() + ")" : "null"));
                    System.out.println("  My Final Previous: " + (previousNode != null ? previousNode.getID() + " (" + previousNode.getIP() + ")" : "null"));

                    //we only need the next node of failed node (previous is this node)
                    Map.Entry<Integer, String> nextEntry = nextAndPrevious.entrySet().stream().min(Map.Entry.comparingByKey()).orElse(null);
                    Node failedNextNode = new Node(nextEntry.getKey() , nextEntry.getValue());

                    System.out.println("FailedNExtNode : " + failedNextNode);


                    setOtherPreviousNode(failedNextNode.getIP() , currentNode , failedNextNode.getIP());
                    setNextNode(failedNextNode);

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




    public void shutdown(){

        //before remove node out of network -> file transfer
        replicationService.shutdown();

        //remove node from network
        setOtherPreviousNode(nextNode.getIP(), nextNode , "Set Other Next");
        setOtherNextNode(previousNode.getIP(), previousNode , "Set other previous");

        String mapping = "/namingserver" + "/node/by-id/" + currentNode.getID();
        String deleteUri = "http://" + NNConf.NAMINGSERVER_HOST + ":" + NNConf.NAMINGSERVER_PORT + mapping ;

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.delete(deleteUri);

        System.exit(0);
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

    public void setBiggest(boolean biggest) {
        this.biggest = biggest;
    }
}