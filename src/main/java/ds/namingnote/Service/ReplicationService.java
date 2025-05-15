package ds.namingnote.Service;

import ds.namingnote.Config.NNConf;
import ds.namingnote.FileCheck.FileChecker;
import ds.namingnote.Utilities.Node;
import ds.namingnote.Utilities.ReferenceDTO;
import ds.namingnote.model.LocalJsonMap;
import jakarta.validation.constraints.Null;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static ds.namingnote.Config.NNConf.*;

@Service
public class ReplicationService {


    private Thread fileCheckerThread = null;

    private LocalJsonMap<String , String> whoReplicatedMyFiles;

    private LocalJsonMap<String, String> filesIReplicated ;


    @Autowired
    private NodeService nodeService;

    public ReplicationService() {
        this.whoReplicatedMyFiles = new LocalJsonMap<>(whoHas_MAP_PATH);
        this.filesIReplicated = new LocalJsonMap<>(localRep_MAP_PATH);
    }


    /**
     * Method start
     * Will go over all files in FILES_DIR and checks through fileAdded
     *
     *
     */
    public void start(){

        File dir = new File(FILES_DIR);  //get files dir
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            //loop over files
            for (File child : directoryListing) {
                fileAdded(child);
            }
            System.out.println("All Files are checked and replicated if needed");
            //here all the files should be checked, so a thread can be started to check for updated in the file DIR
            System.out.println(fileCheckerThread);
            if (fileCheckerThread != null) {
                System.out.println("Creating new file checker and starting thread");

                fileCheckerThread = new Thread(new FileChecker(this));
                fileCheckerThread.start();
            }
        } else {
            System.out.println("Fault with directory : " + FILES_DIR);
        }
    }


    /**
     * Method fileAdded
     * Will check with naming server if file belongs to itself or if it needs replication
     * Will also preform the replication through sendFile
     * @param file  file that is added
     */
    public void fileAdded(File file){


        String mapping = "/node/by-filename/" + file.getName();
        String uri = "http://"+NAMINGSERVER_HOST+":"+ NNConf.NAMINGSERVER_PORT +mapping;

        RestTemplate restTemplate = new RestTemplate();

        try {

            //get ip of node that file belongs to from the naming server
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.POST, null, String.class);
            String ipOfNode = response.getBody(); // the response should contain the ip of the node the file belongs to

            //get own IP
            InetAddress localHost = InetAddress.getLocalHost();           // (MAYBE GET FROM NODESERVICE?)

            //if ip the file belongs to in not from this node -> send to right node
            if (!ipOfNode.equals(localHost.getHostAddress())) {
                if(whoReplicatedMyFiles.containsKey(file.getName())){
                    if (whoReplicatedMyFiles.get(file.getName()).contains(ipOfNode)){
                        //if this is the case the node where we want to send the file already has the file
                        //-> we can skip this
                        return;
                    }
                }


                System.out.println("The file : "+ file.getName() + " Needs to be send to : " + ipOfNode);

                //this node isn't the right one -> send file to ip and save ip in register

                //should do check or execution if file transfer not completed!!!!
                ResponseEntity<String> check = sendFile(ipOfNode , file , null);
                System.out.println("Response of node to file transfer : " + check.getStatusCode());

                //save ip to filename (reference)
                whoReplicatedMyFiles.putSingle(file.getName() , ipOfNode);

            }
            else
                System.out.println("The file : " + file.getName() + " Is already on right node");

        } catch (Exception e) {
            System.out.println("Exception in communication between nodes " + e.getMessage() + " -> handleFailure");
        }
    }


    /**
     * Method sendFile
     * Sends a file to a node
     * @param ipToSendTo ip of node to send to
     * @param file file to send to node
     * @param IpOfRefrenceToSet if not null -> this will be added as given ip to request (see controller)
     * @return response of the node
     */
    public ResponseEntity<String> sendFile(String ipToSendTo ,File file , String IpOfRefrenceToSet  )  {
        final String uri;

        //this is done to make the reference of files customizable and not only senders
        if (IpOfRefrenceToSet != null){
            //send file and also send ip that is the reference to the file
            uri = "http://"+ipToSendTo+":"+NNConf.NAMINGNODE_PORT+"/node/file/"+IpOfRefrenceToSet;
        }else
            //only send file -> refrence ip will be ip of sender (this node)
            uri = "http://"+ipToSendTo+":"+NNConf.NAMINGNODE_PORT+"/node/file";



        // Create headers for multipart form-data
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Create a MultiValueMap to hold the file data
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file)); // Wrap the File in FileSystemResource

        // Create HttpEntity with body and headers
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        // Create a RestTemplate instance
        RestTemplate restTemplate = new RestTemplate();

        // Send the HTTP POST request
        ResponseEntity<String> response = restTemplate.exchange(
                uri, HttpMethod.POST, entity, String.class);

        return response;

    }


    /**
     * Method getFile
     * Search for a file on the local disc (FILES_DIR)
     *
     * @param filename name of file to look for
     * @return ResponsEntity with ok and file if file is found, otherwise a ResponseStatusException
     */
    public ResponseEntity<Resource> getFile(String filename , String ipOfRequester)  {

        Path path = Paths.get(FILES_DIR + filename);

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

            //add ip of the requester to references
            whoReplicatedMyFiles.putSingle(filename,ipOfRequester);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                    .body(resource);

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading file", e);
        }

    }

    /**
     * Method putFile
     * Puts a given file in the local directory (FILES_DIR)
     *
     * @param file file to save to disc
     * @return ResponsEntity with status of saving operation
     */

    public ResponseEntity<String> putFile(MultipartFile file , String ipOfRefrence)  {

        //this is for shutdown purposes

        //if i own the file as a reference -> there is a refrence so its all good
        if (filesIReplicated.containsKey(file.getName())){
            //I already own a replicate of the file nothing
            return ResponseEntity.status(HttpStatus.CONTINUE)
                    .body("I have a replicate already");
        }

        if (whoReplicatedMyFiles.containsKey(file.getName())){
            //here this node has the file locally -> send to previous again
            if (nodeService.getCurrentNode() != nodeService.getCurrentNode()){
                sendFile(nodeService.getPreviousNode().getIP(), (File) file,  nodeService.getCurrentNode().getIP());
                return ResponseEntity.status(HttpStatus.CONTINUE)
                        .body("I have send the file to my previous also");
            }else{

            }
        }

        ////////////

        try {

            // Define the directory where files should be saved
            File directory = new File(FILES_DIR);

            // Create the directory if it does not exist
            if (!directory.exists()) {
                directory.mkdirs();  // Creates the directory and parent directories if needed
            }

            // Save the file on disc
            String fileName = file.getOriginalFilename();
            File destFile = new File(FILES_DIR, fileName);
            System.out.println("File saved to: " + destFile.getAbsolutePath());
            file.transferTo(destFile.toPath());

            //here we need to save where the file came from (we store the replication)
            filesIReplicated.putSingle(fileName , ipOfRefrence);


            return ResponseEntity.ok("File uploaded successfully: " + fileName);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload the file");
        }
    }






    public void shutdown(){

        //so transfer replicated files and references (localRepFiles) to previous node,
        //so we need to send a file and ip of owner ig

        //so we can use uploadFileGivenIP from the controller

        File dir = new File(FILES_DIR);  //get files dir
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            //loop over files and check if reapplication
            for (File child : directoryListing) {
                //if name of file in replication files
                String uri ;

                if (filesIReplicated.containsKey(child.getName())) {
                    //send to previous node with ip found in map                                //FIX THIS!!!!
                    sendFile(nodeService.previousNode.getIP(), child, filesIReplicated.get(child.getName()).get(0));
                    String mapping = "/node/reference/referenceGone";
                    uri = "http://" + whoReplicatedMyFiles.get(child.getName()).get(0) + ":" + NAMINGNODE_PORT + mapping;

                    System.out.println("Calling to: " + uri);


                }

                //if name of file in whoHasRepFile
                if (whoReplicatedMyFiles.containsKey(child.getName())) {
                    // whoHasRepFile for every File stores an IP that the file was replicated to, this is the
                    // owner of the file. We need to warn the owner that this download location (this node IP) is no longer
                    // available. So we need to search through the LocalRepFiles of the owner of this file and remove the
                    // reference it has to the Ip of this node that is shutting down
                    String mapping = "/node/reference/localGone";
                    uri = "http://" + whoReplicatedMyFiles.get(child.getName()).get(0) + ":" + NAMINGNODE_PORT + mapping;

                    System.out.println("Calling to: " + uri);


                } else {
                    continue;
                }

                try {
                    RestTemplate restTemplate = new RestTemplate();

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON); // Indicate that we are sending JSON
                    HttpEntity<ReferenceDTO> requestEntity = new HttpEntity<>(new ReferenceDTO(nodeService.getCurrentNode().getIP() , child.getName()), headers);

                    ResponseEntity<String> response = restTemplate.exchange(
                            uri, HttpMethod.PUT, requestEntity, String.class);
                    System.out.println(response.getBody());
                } catch (Exception e) {
                    System.out.println("Exception in removing local reference from owner of node: " + e.getMessage());
                }





            }

        } else {
            System.out.println("Fault with directory : " + FILES_DIR);
        }

        whoReplicatedMyFiles.deleteJsonFile();
        filesIReplicated.deleteJsonFile();

    }


    public ResponseEntity<String> iHaveYourReplicateAndYouDontExistAnymore(String fileName, String ipOfRef) {
        boolean removed = filesIReplicated.removeValue(fileName, ipOfRef);
        if (removed) {
            String message = String.format("Reference %s removed successfully for file: %s " +
                            "\nIn other words: %s is no longer an available download location for file: %s",
                    ipOfRef, fileName, ipOfRef, fileName
            );
            return ResponseEntity.ok(message);
        } else {
            String message = String.format("Failed to remove reference %s for file: %s ",
                    ipOfRef, fileName
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(message);
        }
    }

    public ResponseEntity<String> iHaveLocalFileAndReplicationIsGone(String fileName, String ipOfRef) {
        boolean removed = whoReplicatedMyFiles.removeValue(fileName, ipOfRef);
        if (removed) {
            String message = String.format("Reference %s removed successfully for file: %s " +
                            "\nIn other words: %s is no longer an available download location for file: %s",
                    ipOfRef, fileName, ipOfRef, fileName
            );
            return ResponseEntity.ok(message);
        } else {
            String message = String.format("Failed to remove reference %s for file: %s ",
                    ipOfRef, fileName
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(message);
        }
    }




}




