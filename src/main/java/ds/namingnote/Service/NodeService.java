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


    private boolean namingServerResponse = false;

    private Thread multicastSenderThread;
    private Thread multicastListenerThread;

    private boolean listenerStarted = false;

    @Autowired
    private ReplicationService replicationService;

    private Thread syncAgentThread;

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
        syncAgentThread = new Thread(new SyncAgent());

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

            System.out.println("Multicast stops");
            multicastSenderThread.interrupt(); //stop the sending thread
            multicastListenerThread.start();  //start the listening thread
            listenerStarted = true;
            replicationService.start();  //start the replication phase
            syncAgentThread.start();
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
        if (currentNode == nextNode && currentNode == previousNode){

            //now there are 2 node, so they both need to set their neighbors to each other.

            //set previous and next of other node

            setOtherNextNode(ip, nextNode, name);
            setOtherPreviousNode(ip, nextNode, name);

            //set previous and next of this node
            nextNode = incommingNode;
            previousNode = incommingNode;

            System.out.println("Node : " + currentNode.getID() + " .Multicast Processed, 2 Nodes On Network");
            replicationService.start();
            return;
        }else {

            if (nameHash > previousNode.getID()) {

                //this node will be placed as nextID of the new node.
                setOtherNextNode(ip, currentNode, name);

                //the new node needs to be previous of this node
                setPreviousNode(incommingNode);

                System.out.println("Node : " + currentNode.getID() + " .Multicast Processed, new previous node : " + name);

            }
            if (nameHash < nextNode.getID()) {


                //this node will be previousID of new node
                setOtherPreviousNode(ip, currentNode, name);

                //the new node needs to be next of this node
                setNextNode(incommingNode);

                System.out.println("Node : " + currentNode.getID() + " .Multicast Processed, new next node : " + name);
            }
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
        try {
            RestTemplate restTemplate = new RestTemplate(); // Use the bean
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM); // Sending serialized Java object

            HttpEntity<Serializable> requestEntity = new HttpEntity<>(agent, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Agent successfully forwarded to " + targetNode.getIP() + ". Response: " + response.getBody());
            } else {
                System.out.println("Failed to forward agent to " + targetNode.getIP() + ". Status: " +
                        response.getStatusCode() + ", Body: " + response.getBody());
                // Potentially handle failure to forward (e.g., if targetNode also failed)
            }
        } catch (Exception e) {
            System.out.println("Error forwarding agent to " + targetNode.getIP()+ e);
            // This could trigger another failure detection for targetNode
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
}