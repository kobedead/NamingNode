package ds.namingnote.Service;

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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class NodeService {

    Node currentNode =null;
    Node nextNode = null;
    Node previousNode = null;


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

        //create the threads for the multicasters
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

            System.out.println("Multicast stops");
            multicastSenderThread.interrupt(); //stop the sending thread
            multicastListenerThread.start();  //start the listening thread
            listenerStarted = true;
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
        if (currentNode == nextNode && currentNode == previousNode){

            //now there are 2 node, so they both need to set their neighbors to each other.

            //set previous and next of other node
            setOtherNextNode(ip , nextNode, name);
            setOtherPreviousNode(ip , nextNode, name);

            //set previous and next of this node
            nextNode = incommingNode;
            previousNode = incommingNode;

            System.out.println("Node : "+currentNode.getID() +" .Multicast Processed, 2 Nodes On Network");
            return;
        }

        if (nameHash > previousNode.getID()){

            //this node will be placed as nextID of the new node.
            setOtherNextNode(ip , currentNode, name);

            //the new node needs to be previous of this node
            setPreviousNode(incommingNode);

            System.out.println("Node : "+ currentNode.getID() +" .Multicast Processed, new previous node : "+ name);

        }
        if (nameHash < nextNode.getID()){
            //this node will be previousID of new node
            setOtherPreviousNode(ip , currentNode, name);

            //the new node needs to be next of this node
            setNextNode(incommingNode);

            System.out.println("Node : "+ currentNode.getID() +" .Multicast Processed, new next node : "+ name);


        }

    }



    public ResponseEntity<String> setOtherNextNode(String ip , Node node, String name){

        String mapping = "/node/id/next";
        String uri = "http://" + ip + ":" + NNConf.NAMINGNODE_PORT + mapping;

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON); // Indicate that we are sending JSON
        HttpEntity<Node> requestEntity = new HttpEntity<>(node, headers);

        System.out.println("setOtherNextID for node" + name + " on ip " + ip + " with Node object: ID=" + node.getID() + ", IP=" + node.getIP());
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

        System.out.println("setOtherPreviousID for node" + name + " on ip " + ip + "with Node object: ID=" + node.getID() + ", IP=" + node.getIP());

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
            System.out.println("Ping to " + label + " node (" + node.getID() + ") , ip : "+ node.getIP() +" successful: " + response);
        } catch (Exception e) {
            System.err.println("Failed to ping " + label + " node (" + node.getID() +  ") , ip : "+ node.getIP() +" successful: " + e.getMessage());
            handleFailure(node);
        }
    }

    public void handleFailure(Node node) {
        String baseUri = "http://" + NNConf.NAMINGSERVER_HOST + ":" + NNConf.NAMINGSERVER_PORT + "/namingserver";
        RestTemplate restTemplate = new RestTemplate();

        try {
            //get the failed node next and previous nodes from naming server
            String getUri = baseUri + "/node/nextAndPrevious/" + node.getID();
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
                System.out.println("Next and Previous for node " + node.getIP() + ": " + nextAndPrevious);

                // Set the next id of the previous node to the next id of the failed node
                if (nextAndPrevious.keySet().size() == 2) {
                    //extract previous and next node of failed node from part map
                    Map.Entry<Integer, String> nextEntry = nextAndPrevious.entrySet().stream().max(Map.Entry.comparingByKey()).orElse(null);
                    Map.Entry<Integer, String> previousEntry = nextAndPrevious.entrySet().stream().min(Map.Entry.comparingByKey()).orElse(null);

                    Node failedPreviousNode = new Node(previousEntry.getKey() , previousEntry.getValue());
                    Node failedNextNode = new Node(nextEntry.getKey() , nextEntry.getValue());

                    //set previous of next to previous of failed
                    setOtherPreviousNode(failedNextNode.getIP() , previousNode , "Set previous of next to previous of failed");
                    //set next of previous to next of failed
                    setOtherNextNode(failedPreviousNode.getIP() , nextNode , "set next of previous to next of failed" );

                } else if (nextAndPrevious.keySet().size() == 1) { // Happens if previous == next
                    // only one other node on network -> set previous and next to its own
                    Node onlyOne = new Node(nextAndPrevious.keySet().stream().findFirst().get() ,nextAndPrevious.values().stream().findFirst().get());
                    setOtherNextNode(onlyOne.getIP(), onlyOne , "Set onlyOne next to own");
                    setOtherPreviousNode(onlyOne.getIP() , onlyOne , "Set onlyOne previous to own");
                }


            } else {
                System.out.println("Failed to retrieve next and previous info for node: " + node.getIP());
            }
        } catch (Exception e) {
            System.err.println("Error fetching next and previous info: " + e.getMessage());
        }
        //if successfully set everything we can delete the failed node from the naming server
        try {
            String deleteUri = baseUri + "/node/by-id/" + node.getID();
            restTemplate.delete(deleteUri);
            System.out.println("Node " + node.getID() + " removed successfully.");
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





    public Node getPreviousNode () {
        return previousNode;
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