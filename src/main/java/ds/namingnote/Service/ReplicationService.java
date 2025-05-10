package ds.namingnote.Service;

import ds.namingnote.Config.NNConf;
import ds.namingnote.FileCheck.FileChecker;
import ds.namingnote.model.LocalJsonMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

import static ds.namingnote.Config.NNConf.*;

@Service
public class ReplicationService {


    private Thread fileCheckerThread;

    /// Keeps track of files that originally existed on this node, but were sent to another
    private LocalJsonMap<String , String> whoHasRepFile;

    /// Keeps track of files that have been replicated to this node by another node
    private LocalJsonMap<String, String> localRepFiles ;


    @Autowired
    @Lazy
    private NodeService nodeService;

    public ReplicationService() {
        this.whoHasRepFile = new LocalJsonMap<>(whoHas_MAP_PATH);
        this.localRepFiles = new LocalJsonMap<>(localRep_MAP_PATH);


        fileCheckerThread = new Thread(new FileChecker(this));

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
            //here all the files should be checked, so a thread can be started to check for updated in the file DIR
            fileCheckerThread.start();

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


        String mapping = "/namingserver/node/by-filename/" + file.getName();
        String uri = "http://" + NNConf.NAMINGSERVER_HOST + ":" + NNConf.NAMINGSERVER_PORT + mapping;

        RestTemplate restTemplate = new RestTemplate();

        try {
            //get ip of node that file belongs to from the naming server
            System.out.println("Calling to: " + uri);
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, null, String.class);
            String ipOfNode = response.getBody(); // the response should contain the ip of the node the file belongs to
            System.out.println("File " + file.getName() + " should be added to " + ipOfNode + " according to NamingServer");

            //get own IP
            InetAddress localHost = InetAddress.getLocalHost();           // (MAYBE GET FROM NODESERVICE?)

            //if ip the file belongs to in not from this node -> send to right node
            if (!ipOfNode.equals(localHost.getHostAddress())) {

                System.out.println("The file : "+ file.getName() + " Needs to be send to : " + ipOfNode);

                //this node isn't the right one -> send file to ip and save ip in register

                //should do check or execution if file transfer not completed!!!!
                ResponseEntity<String> check = sendFile(ipOfNode , file , null);
                System.out.println("Response of node to file transfer : " + check.getStatusCode());

                //save ip to filename (reference)
                whoHasRepFile.putSingle(file.getName() , ipOfNode);

            }
            else
                System.out.println("The file : " + file.getName() + " Is already on right node");

        } catch (Exception e) {
            System.out.println("Exception in communication between nodes (fileAdded function) " + e.getMessage());
        }
    }


    /**
     * Method sendFile
     * Sends a file to a node
     * @param ip ip of node to send to
     * @param file file to send to node
     * @param givenIp if not null -> this will be added as given ip to request (see controller)
     * @return response of the node
     */
    public ResponseEntity<String> sendFile(String ip ,File file , String givenIp  )  {
        final String uri;

        //this is done to make the reference of files customizable and not only senders
        if (givenIp != null){
            uri = "http://"+ip+":"+NNConf.NAMINGNODE_PORT+"/node/file/"+givenIp;
        }else
            uri = "http://"+ip+":"+NNConf.NAMINGNODE_PORT+"/node/file";



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
            whoHasRepFile.putSingle(filename,ipOfRequester);

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

    public ResponseEntity<String> putFile(MultipartFile file , String ipOfRequester)  {

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
            localRepFiles.putSingle(fileName , ipOfRequester);


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
                if (localRepFiles.containsKey(child.getName())){
                    //send to previous node with ip found in map                                //FIX THIS!!!!
                    sendFile(nodeService.previousNode.getIP() , child , localRepFiles.get(child.getName()).get(0));

                    //WE NEED TO ALSO CHECK IF GOTTEN FILE IS ALREADY SAVED ON NODE, IF IT IS -> SEND TO PREVIOUS AGAIN
                    //MAYBE ALSO CHECK FOR LOOPS??

                }

                //if name of file in whoHasRepFile
                if (whoHasRepFile.containsKey(child.getName())){
                    // whoHasRepFile for every File stores an IP that the file was replicated to, this is the
                    // owner of the file. We need to warn the owner that this download location (this node IP) is no longer
                    // available. So we need to search through the LocalRepFiles of the owner of this file and remove the
                    // reference it has to the Ip of this node that is shutting down
                    String mapping = "/node/file/removeLocalReference/" + child.getName();
                    String uri = "http://" + whoHasRepFile.get(child.getName()).get(0) +":" + NAMINGNODE_PORT + mapping;

                    System.out.println("Calling to: " + uri);

                    try {
                        RestTemplate restTemplate = new RestTemplate();

                        ResponseEntity<String> response = restTemplate.exchange(
                                uri, HttpMethod.PUT, new HttpEntity<>(InetAddress.getLocalHost()), String.class);
                        System.out.println(response.getBody());
                    } catch (Exception e) {
                        System.out.println("Exception in removing local reference from owner of node: " + e.getMessage());
                    }


                    //not clear to me what needs to be done here exactly

                    //or we remove all the files from the whole network
                    //-> go over all files and notify all the linked ip's


                    //or we notify the replicated nodes that the download location has been removed
                    //so one of the replicated nodes becomes the owner and the references need to be updated


                    //or we need to also move the file to previous node, but this seems more stupid



                } else {
                    //nobody has a replicated file -> file can be removed
                    //or do nothing and shut down the node ig
                }








            }
            //here all the files should be checked, so a thread can be started to check for updated in the file DIR
            //fileCheckerThread.start();

        } else {
            System.out.println("Fault with directory : " + FILES_DIR);
        }


    }


    public ResponseEntity<String> removeLocalReference(String fileName, String ipOfRef) {
        try {
            localRepFiles.removeValue(fileName , ipOfRef);
            return ResponseEntity.ok("File removed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to remove local reference from owner of the file");
        }
    }
}
