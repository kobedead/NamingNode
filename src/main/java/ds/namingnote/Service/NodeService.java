package ds.namingnote.Service;

import ds.namingnote.Config.NNConf;
import ds.namingnote.Multicast.MulticastSender;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
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

@Service
public class NodeService {

    private int currentID;
    private int previousID = -10;
    private int nextID = -10;

    private boolean jointNetwork = false;


    public void setNameBegin(String name) throws IOException {
        //this will get the name from config and start everything up.

        currentID = mapHash(name);

        MulticastSender multicastSender = new MulticastSender();

        //sending multicast message until we get anwser
        while(!jointNetwork && nextID == -10 && previousID == -10){
            multicastSender.sendName(name);

            //this will loop so try to fix the looping texts
            if(jointNetwork)
                System.out.println("Got message from server");
            if(nextID != -10)
                System.out.println("got message from other node : Next updated");
            if(previousID !=-10)
                System.out.println("got message from other node : Previous updated");
        }

        //here this node has full joint the server -> needs to start listening to multicasts

        //start the multicast that is using async.


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

            setOtherNextID(ip , nextID);
            setOtherPreviousID(ip , previousID);

            previousID = nameHash;
            nextID =nameHash;

            System.out.println("Node : "+currentID+" .Multicast Processed, 2 Nodes On Network");

            return;
        }

        if (nameHash > previousID){
            //this node will be placed as nextID of the new node.
            setOtherNextID(ip , currentID);

            //the new node needs to be previous of this node
            setPreviousID(nameHash);

            System.out.println("Node : "+currentID+" .Multicast Processed, new previous node : "+ name);


        }
        if (nameHash < nextID){
            //this node will be previousID of new node
            setOtherPreviousID(ip , currentID);

            //the new node needs to be next of this node
            setNextID(nameHash);

            System.out.println("Node : "+currentID+" .Multicast Processed, new next node : "+ name);


        }

    }



    public ResponseEntity<String> setOtherNextID(String ip , int ID){

        String mapping = "node/id/next/";

        String uri = "http://"+ip+":"+ NNConf.NAMINGNODE_PORT +mapping+ID;

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(
                uri, HttpMethod.POST, null, String.class);

        System.out.println("setOtherNextID : " +response.getBody());  //we need to check for error ig

        return  response;                                  //check

    }


    public ResponseEntity<String> setOtherPreviousID(String ip , int ID){

        String mapping = "node/id/previous/";

        String uri = "http://"+ip+":"+NNConf.NAMINGNODE_PORT+mapping+ID;

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(
                uri, HttpMethod.POST, null, String.class);

        System.out.println("setOtherPreviousID : " +response.getBody());  //we need to check for error ig

        return  response;                                  //check

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
        if (numberOfNodes == 0) {
            /// This is the only node in the network
            ///  TODO: set previousNodeHash and nextNodeHash to be its own Hash value

        } else {
            /// There are other nodes in this network
            ///  The node should receive parameters for its next and previous node
            ///  Other nodes should send this after receiving the Multicast
            ///  This node expects a call on its REST endpoints to set the previous and next node.
        }


    }






    public int getPreviousID () {
        return previousID;
    }

    public void setPreviousID ( int previousID){
        this.previousID = previousID;
    }

    public int getNextID () {
        return nextID;
    }

    public void setNextID ( int nextID){
        this.nextID = nextID;
    }



}