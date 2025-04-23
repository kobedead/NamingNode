package ds.namingnote.Service;

import ds.namingnote.Config.NNConf;
import ds.namingnote.Multicast.MulticastListener;
import ds.namingnote.Multicast.MulticastSender;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class NodeService {

    private int currentID;
    private int previousID = -10;
    private int nextID = -10;

    private String nextIP = "";
    private String previousIP = "";

    private boolean namingServerResponse = false;
    private MulticastListener multicastListener;
    private MulticastSender multicastSender;

    private boolean listenerStarted = false;

    private Thread multicastSenderThread;

    private Thread multicastListenerThread;

    //set name (bit of constructor ig)
    public void setNameBegin(String name) throws IOException {

        this.multicastListener = new MulticastListener(this);
        this.multicastSender = new MulticastSender(name);

        currentID = mapHash(name);

        multicastSenderThread = new Thread(multicastSender);
        multicastListenerThread = new Thread(multicastListener);

        //begin sending messages
        multicastSenderThread.start();
    }



    public void checkConnection(){

        // if node is connected -> stop sending and start listening
        if(!listenerStarted && namingServerResponse && nextID != -10 && previousID != -10) {

            System.out.println("Multicast stops");

            multicastSenderThread.interrupt();                                //bad but yea

            multicastListenerThread.start();

            listenerStarted = true;

            return;
        }

        System.out.println("CurrentID = " + currentID);
        if(namingServerResponse)
            System.out.println("Got message from server");
        if(nextID != -10)
            System.out.println("got message from other node : Next updated");
        if(previousID !=-10)
            System.out.println("got message from other node : Previous updated");

    }



    public ResponseEntity<Resource> getFile(String filename) throws FileNotFoundException {

        Path path = Paths.get("Files/" + filename);

        // Check if file exists
        if (Files.notExists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File Not Found");
        }

        try {
            File file = path.toFile();
            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));

            // Try to determine the content type dynamically
            String contentType = Files.probeContentType(path);
            if (contentType == null) {
                contentType = "application/octet-stream";  // Default fallback
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                    .body(resource);

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading file", e);
        }

    }


    public ResponseEntity<String> putFile(MultipartFile file)  {

        try {

            // Define the directory where files should be saved
            File directory = new File("Files");

            // Create the directory if it does not exist
            if (!directory.exists()) {
                directory.mkdirs();  // Creates the directory and parent directories if needed
            }

            // Save the file on disc
            String fileName = file.getOriginalFilename();
            File destFile = new File("Files", fileName);
            System.out.println("File saved to: " + destFile.getAbsolutePath());
            file.transferTo(destFile.toPath());

            return ResponseEntity.ok("File uploaded successfully: " + fileName);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload the file");
        }
    }

    public void processIncomingMulticast(String ip, String name){
        int nameHash = mapHash(name);

        //new node is the only one with me on network
        if (currentID == nextID && currentID == previousID){

            //now there are 2 node, so they both need to set their neighbors to each other.

            setOtherNextID(ip , nextID, name);
            setOtherPreviousID(ip , previousID, name);

            previousID = nameHash;
            nextID = nameHash;
            nextIP = ip;
            previousIP = ip;

            System.out.println("Node : "+currentID+" .Multicast Processed, 2 Nodes On Network");

            return;
        }

        if (nameHash > previousID){
            //this node will be placed as nextID of the new node.
            setOtherNextID(ip , currentID, name);

            //the new node needs to be previous of this node
            setPreviousID(nameHash);

            System.out.println("Node : "+currentID+" .Multicast Processed, new previous node : "+ name);


        }
        if (nameHash < nextID){
            //this node will be previousID of new node
            setOtherPreviousID(ip , currentID, name);

            //the new node needs to be next of this node
            setNextID(nameHash);

            System.out.println("Node : "+currentID+" .Multicast Processed, new next node : "+ name);


        }

    }



    public ResponseEntity<String> setOtherNextID(String ip , int ID, String name){

        String mapping = "/node/id/next/";

        String uri = "http://"+ip+":"+ NNConf.NAMINGNODE_PORT +mapping+ID;

        RestTemplate restTemplate = new RestTemplate();

        System.out.println("setOtherNextID for node" + name + " on ip " + ip);
        System.out.println("Call to " + uri);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.POST, null, String.class);

            System.out.println("setOtherNextID : " +response.getBody());  //we need to check for error ig

            return  response;                                  //check
        } catch (Exception e) {
            System.out.println("Exception in communication between nodes " + e.getMessage() + " -> handleFailure");
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    public ResponseEntity<String> setOtherPreviousID(String ip , int ID, String name){

        String mapping = "/node/id/previous/";

        String uri = "http://"+ip+":"+NNConf.NAMINGNODE_PORT+mapping+ID;

        RestTemplate restTemplate = new RestTemplate();

        System.out.println("setOtherPreviousID for node" + name + " on ip " + ip);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.POST, null, String.class);

            System.out.println("setOtherPreviousID : " +response.getBody());  //we need to check for error ig

            return  response;                                  //check
        } catch (Exception e) {
            // If communication between nodes fails, execute failure
            System.out.println("Exception in communication between nodes " + e.getMessage() + " -> handleFailure");
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



    /**
     * Hashing function to hash incoming names (based on given hashing algorithm)
     * @param text name of the node or file to be hashed
     * @return hashed integer value
     */
    public int mapHash (String text){
        int hashCode = text.hashCode();
        int max = Integer.MAX_VALUE;
        int min = Integer.MIN_VALUE;

        // Ensure the hashCode is always positive
        int adjustedHash = Math.abs(hashCode);

        // Mapping hashCode from (Integer.MIN_VALUE, Integer.MAX_VALUE) to (0, 32768)
        return (int) (((long) adjustedHash * 32768) / ((long) max - min));
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
            previousID = currentID;
            nextID = currentID;

        } else {
            /// There are other nodes in this network
            ///  The node should receive parameters for its next and previous node
            ///  Other nodes should send this after receiving the Multicast
            ///  This node expects a call on its REST endpoints to set the previous and next node.

        }
        this.checkConnection();
    }

    public void handleFailure(String ip) {
        String baseUri = "http://" + NNConf.NAMINGSERVER_HOST + ":" + NNConf.NAMINGSERVER_PORT + "/namingserver";
        RestTemplate restTemplate = new RestTemplate();

        try {
            String getUri = baseUri + "/node/nextAndPrevious/" + ip;
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
                System.out.println("Next and Previous for node " + ip + ": " + nextAndPrevious);

                // Set the next id of the previous node to the next id of the failed node
                if (nextAndPrevious.keySet().size() == 2) {
                    Map.Entry<Integer, String> nextEntry = nextAndPrevious.entrySet().stream().max(Map.Entry.comparingByKey()).orElse(null);
                    Map.Entry<Integer, String> previousEntry = nextAndPrevious.entrySet().stream().min(Map.Entry.comparingByKey()).orElse(null);
                    setOtherNextID(previousEntry.getValue(), previousEntry.getKey(), ip);
                    setOtherPreviousID(nextEntry.getValue(), nextEntry.getKey(), ip);
                } else if (nextAndPrevious.keySet().size() == 1) { // Happens if previous == next
                    setOtherNextID(nextAndPrevious.values().stream().findFirst().get() ,nextAndPrevious.keySet().stream().findFirst().get(), ip);
                    setOtherPreviousID(nextAndPrevious.values().stream().findFirst().get() ,nextAndPrevious.keySet().stream().findFirst().get(), ip);
                }


            } else {
                System.out.println("Failed to retrieve next and previous info for node: " + ip);
            }
        } catch (Exception e) {
            System.err.println("Error fetching next and previous info: " + e.getMessage());
        }

        try {
            String deleteUri = baseUri + "/node/by-ip/" + ip;
            restTemplate.delete(deleteUri);
            System.out.println("Node " + ip + " removed successfully.");
        } catch (Exception e) {
            System.err.println("Error deleting node: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 30000) // Runs every 30 seconds
    public void pingNextAndPreviousNode() {
        System.out.println("Pinging previous and next nodes...");

        pingNode(previousIP, "previous");
        pingNode(nextIP, "next");
    }

    private void pingNode(String ip, String label) {
        if (Objects.equals(ip, "")) {
            System.out.println(label + " IP is null, skipping ping.");
            return;
        }

        String url = "http://" + ip + ":" + NNConf.NAMINGNODE_PORT + "/node/ping";
        RestTemplate restTemplate = new RestTemplate();

        try {
            String response = restTemplate.getForObject(url, String.class);
            System.out.println("Ping to " + label + " node (" + ip + ") successful: " + response);
        } catch (Exception e) {
            System.err.println("Failed to ping " + label + " node (" + ip + "): " + e.getMessage());
            handleFailure(ip);
        }
    }

    private String fetchIpById(int id) {
        String url = "http://" + NNConf.NAMINGSERVER_HOST + ":" + NNConf.NAMINGSERVER_PORT + "/namingserver/node/by-id/" + id;

        try {
            RestTemplate restTemplate = new RestTemplate();
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            System.err.println("Failed to fetch IP for ID " + id + ": " + e.getMessage());
            return null;
        }
    }


    public void shutdown(){

        setOtherNextID(previousIP,previousID, "");
        setOtherPreviousID(nextIP,nextID,"");

        String deleteUri = "http://" + NNConf.NAMINGSERVER_HOST + ":" + NNConf.NAMINGSERVER_PORT + "/namingserver" + "/node/by-id/" + currentID;
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.delete(deleteUri);

        System.exit(0);

    }





    public int getPreviousID () {
        return previousID;
    }

    public void setPreviousID(int previousID) {
        this.previousID = previousID;
        this.previousIP = fetchIpById(previousID);
        System.out.println("Previous ID set to " + previousID + " with IP: " + previousIP);
    }

    public void setNextID(int nextID) {
        this.nextID = nextID;
        this.nextIP = fetchIpById(nextID);
        System.out.println("Next ID set to " + nextID + " with IP: " + nextIP);
    }

    public int getCurrentID() {
        return currentID;
    }
}