package ds.namingnote.Service;

import ds.namingnote.Agents.FailureAgent;
import ds.namingnote.Config.NNConf;
import ds.namingnote.Controller.AgentController;
import ds.namingnote.Multicast.MulticastListener;
import ds.namingnote.Multicast.MulticastSender;
import ds.namingnote.Utilities.NextAndPreviousIDDTO;
import ds.namingnote.Utilities.Node;
import ds.namingnote.Utilities.NodeDTO;
import ds.namingnote.Utilities.Utilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static ds.namingnote.Config.NNConf.FILES_DIR;

@Service
public class NodeService {

    private static final Logger logger = Logger.getLogger(AgentController.class.getName());

    String name;
    Node currentNode = null;
    Node nextNode = null;
    Node previousNode = null;


    private boolean namingServerResponse = false;

    private Thread multicastSenderThread = null;
    private Thread multicastListenerThread = null;

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

        this.name = name;
        //get own ip
        InetAddress localHost = InetAddress.getLocalHost(); //get own IP
        //create node object for current node
        Node currentnode = new Node(Utilities.mapHash(name) , localHost.getHostAddress());
        //set current node
        this.currentNode =  currentnode ;
        System.out.println("Current node is set: " + currentnode.getIP());


        //create the threads for the multicast
        if (multicastListenerThread == null)
            multicastListenerThread = new Thread(new MulticastListener(this));
        if (multicastSenderThread == null)
            multicastSenderThread = new Thread(new MulticastSender(name));

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
     * @param incomingNodeId ID of node (sender multicast)
     */
    public void processIncomingMulticast(String ip, int incomingNodeId){
        Node incomingNode = new Node(incomingNodeId, ip);

        // 0. Ignore if it's our own multicast
        if (incomingNode.getID() == currentNode.getID()) {
            return;
        }

        System.out.println("Processing Multicast from: (ID: " + incomingNodeId + ") received by Node " + currentNode.getID());
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
            setOtherNextNode(incomingNode.getIP(), currentNode );
            setOtherPreviousNode(incomingNode.getIP(), currentNode);

            replicationService.checkFiles(); // Or other post-join actions
            logFinalState(incomingNodeId);
            return;
        }

        //more than 2 nodes in the network

        boolean fitsAsMyNext = false;
        //we are not the biggest node
        if (currentId < nextId) {
            if (incomingNodeId > currentId && incomingNodeId < nextId) {
                fitsAsMyNext = true;
            }
            //we are the biggest node in the network
        } else {
            if (incomingNodeId > currentId || incomingNodeId < nextId) {
                // incoming is larger than me OR smaller than my next (which is the 'smallest')
                fitsAsMyNext = true;
            }
        }

        if (fitsAsMyNext) {
            System.out.println("  Decision: " + incomingNodeId + " fits as NEW NEXT for " + currentId +
                    " (between " + currentId + " and " + nextId + ")");

            setNextNode(incomingNode);
            // incomingNode's new previous is me (currentNode)
            setOtherPreviousNode(incomingNode.getIP(), currentNode);
            // My new nextNode is incomingNode


            replicationService.checkFiles();
            logFinalState( incomingNodeId);
            return;
        }


        boolean fitsAsMyPrevious = false;
        //this node is not the smallest node in the network
        if (prevId < currentId) {
            if (incomingNodeId > prevId && incomingNodeId < currentId) {
                fitsAsMyPrevious = true;
            }
            //current node is the smallest in the network
        } else {
            //incoming node is smallest or biggest
            if (incomingNodeId > prevId || incomingNodeId < currentId) {
                fitsAsMyPrevious = true;
            }
        }

        if (fitsAsMyPrevious) {
            System.out.println("  Decision: " + incomingNodeId + " fits as NEW PREVIOUS for " + currentId +
                    " (between " + prevId + " and " + currentId + ")");

            setPreviousNode(incomingNode);
            setOtherNextNode(incomingNode.getIP(), currentNode);
            // My new previousNode is incomingNode


            replicationService.start();
            logFinalState(incomingNodeId);
            return;
        }

        // If neither of the above, incomingNode is not an immediate neighbor for this currentNode.
        // Another node in the ring will handle it.
        System.out.println("  Decision: " + incomingNodeId + " is not an immediate neighbor for " + currentId + ". No local pointer changes for this incoming node.");
        logFinalState( incomingNodeId); // Log state even if no change
    }
    private void logFinalState(int processedNodeId) {
        System.out.println("Node " + currentNode.getID() + ": Finished processing multicast from : " + processedNodeId + ")");
        System.out.println("  My Final Next: " + (nextNode != null ? nextNode.getID() + " (" + nextNode.getIP() + ")" : "null"));
        System.out.println("  My Final Previous: " + (previousNode != null ? previousNode.getID() + " (" + previousNode.getIP() + ")" : "null"));
    }



    public ResponseEntity<String> setOtherNextNode(String ip , Node node){

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


    public ResponseEntity<String> setOtherPreviousNode(String ip , Node node ){

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


    public void handleFailure(Node failedNode) {
        String baseUri = "http://" + NNConf.NAMINGSERVER_HOST + ":" + NNConf.NAMINGSERVER_PORT + "/namingserver";
        RestTemplate restTemplate = new RestTemplate();

        try {
            //get the failed node next and previous nodes from naming server
            String getUri = baseUri + "/node/next/" + failedNode.getID();
            ResponseEntity<NodeDTO> response = restTemplate.getForEntity(getUri, NodeDTO.class);


            if (response.getStatusCode() == HttpStatus.OK) {
                NodeDTO nextOfFailed = response.getBody();

                if (nextOfFailed == null) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "NextAndPrevious set returned null");
                }

                //next of failed is me
                if (nextOfFailed.getID() == currentNode.getID()) {
                    getUri = baseUri + "/node/previous/" + failedNode.getID();
                    response = restTemplate.getForEntity(getUri, NodeDTO.class);


                    if (response.getStatusCode() == HttpStatus.OK) {
                        NodeDTO previousOfFailed = response.getBody();

                        if (previousOfFailed == null) {
                            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "NextAndPrevious set returned null");
                        }
                        //previous if failed is me -> im the only node on network
                        if (previousOfFailed.getID() == currentNode.getID()) {
                            setNextNode(currentNode);
                            setPreviousNode(currentNode);
                        } else {
                            //i need to do operations with previous node of failed -> im the next
                            setOtherNextNode(previousOfFailed.getIP(), currentNode);
                            setPreviousNode(new Node(previousOfFailed.getID(), previousOfFailed.getIP()));
                            FailureAgent failureAgent = new FailureAgent(failedNode , previousNode , currentNode);
                            forwardAgent(failureAgent , nextNode);
                        }
                    } else
                        System.out.println("nextOFfAiled is null");

                    //i need to do operations with next of failed node -> im the previous
                } else {
                    setOtherPreviousNode(nextOfFailed.getIP(), currentNode);
                    setNextNode(new Node(nextOfFailed.getID(), nextOfFailed.getIP()));
                    //create the failed agent and forward this
                    FailureAgent failureAgent = new FailureAgent(failedNode , currentNode , currentNode);
                    forwardAgent(failureAgent , nextNode);

                }


            }
        } catch (RestClientException e) {
            throw new RuntimeException(e);
        } catch (ResponseStatusException e) {
            throw new RuntimeException(e);
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



    public void shutdown() throws InterruptedException {
        if (running) {

            System.out.println("Shutdown called");
            //before remove node out of network -> file transfer
            replicationService.shutdown();
            System.out.println("All files should be Managed ");
            //remove node from network
            System.out.println("Setting neighbours to each other, CurrentPrev : " + getPreviousNode() + " CurrentNext : " + getNextNode());
            setOtherPreviousNode(getNextNode().getIP(), getPreviousNode());
            setOtherNextNode(getPreviousNode().getIP(), getNextNode() );

            setNextNode(null);
            setPreviousNode(null);

            System.out.println("Deleting myself from the server map");
            String mapping = "/namingserver" + "/node/by-id/" + currentNode.getID();
            String deleteUri = "http://" + NNConf.NAMINGSERVER_HOST + ":" + NNConf.NAMINGSERVER_PORT + mapping ;

            RestTemplate restTemplate = new RestTemplate();
            restTemplate.delete(deleteUri);
            System.out.println("Shutting down my threads");
            running = false;
            System.out.println("Shutting down my threads");

            multicastListenerThread.join();
            multicastSenderThread.join();
            replicationService.joinSyncAgent();
            replicationService.joinFileChecker();

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

    public ResponseEntity executeFailureAgent(byte[] serializedAgent){
        Node currentNode = getCurrentNode();
        if (currentNode == null) {
            logger.severe("Node not initialized. Cannot execute agent.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Node not initialized");
        }
        logger.info("Node " + currentNode.getID() + " received agent for execution.");

        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedAgent);
             ObjectInputStream ois = new ObjectInputStream(bis)) {

            FailureAgent failureAgent = (FailureAgent) ois.readObject();

            // Initialize transient fields
            failureAgent.initialize(getCurrentNode(), getNextNode(), replicationService , FILES_DIR);

            Thread agentThread = new Thread(failureAgent);
            agentThread.setName("MobileAgentThread-" + failureAgent.getClass().getSimpleName() + "-" + currentNode.getID());
            logger.info("Starting agent thread for: " + failureAgent.getClass().getSimpleName());
            agentThread.start();
            agentThread.join(); // Wait for the agent's run() method to complete
            logger.info("Agent thread finished: " + failureAgent.getClass().getSimpleName());

            // For FailureAgent: forward to next node unless it's back to originator
            Node nextNode = getNextNode();
            if (nextNode != null && nextNode.getID() != currentNode.getID() && currentNode.getID() != failureAgent.getOriginatorNode().getID()) {
                logger.info("Forwarding FailureAgent from " + currentNode.getIP() + " to next node: " + nextNode.getIP());
                forwardAgent(failureAgent, nextNode);
            } else {
                 if (currentNode.getID() == failureAgent.getOriginatorNode().getID()) {
                    logger.info("FailureAgent journey complete on node " + currentNode.getID() + ". Current node (" + currentNode.getID() + ") is originator. Agent terminated.");
                }
            }

            return ResponseEntity.ok("Agent executed successfully on node " + currentNode.getID());
        } catch (ClassNotFoundException | InterruptedException e) {
            throw new RuntimeException(e);
    } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public Node getPreviousNode () {
        return previousNode;
    }
    public Node getNextNode () {
        return nextNode;
    }

    public void setPreviousNode(Node previousNode) {
        if (previousNode != null)
            System.out.println("Previous ID set to " + previousNode.getID() + " with IP: " + previousNode.getIP());

        this.previousNode = previousNode;
    }

    public void setNextNode(Node nextNode) {
        if (nextNode != null)
            System.out.println("Next ID set to " + nextNode.getID() + " with IP: " + nextNode.getIP());

        this.nextNode = nextNode;
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    public boolean isRunning() {
        return running;
    }

    public NextAndPreviousIDDTO getNextAndPrevious() {
        Node next = this.getNextNode();
        Node previous = this.getPreviousNode();
        if (next != null && previous != null ) {
            return new NextAndPreviousIDDTO(next.getID(), previous.getID());
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Next or previous node is null, is this the only node in the network?");
        }
    }
}