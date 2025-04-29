package ds.namingnote.Service;

import ds.namingnote.Config.NNConf;
import ds.namingnote.FileCheck.FileChecker;
import ds.namingnote.model.LocalJsonMap;
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

    private LocalJsonMap<String , String> fileReferences;

    public ReplicationService() {
        this.fileReferences = new LocalJsonMap<>(MAP_PATH);
        fileCheckerThread = new Thread(new FileChecker());

    }


    /**
     * Method start
     * Will go over all files in FILES_DIR and check with naming server is file
     * belongs to itself or if it needs replication
     *
     *
     */
    public void start(){


        File dir = new File(FILES_DIR);  //get files dir
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            //loop over files
            for (File child : directoryListing) {

                String mapping = "/node/by-filename/" + child.getName();
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

                        System.out.println("The file : "+ child.getName() + " Needs to be send to : " + ipOfNode);

                        //this node isn't the right one -> send file to ip and save ip in register

                        //should do check or execution if file transfer not completed!!!!
                        ResponseEntity<String> check = sendFile(ipOfNode , (MultipartFile) child);
                        System.out.println("Response of node to file transfer : " + check.getStatusCode());

                        //save ip to filename (reference)
                        fileReferences.putSingle(child.getName() , ipOfNode);

                    }
                    else
                        System.out.println("The file : " + child.getName() + " Is already on right node");

                } catch (Exception e) {
                    System.out.println("Exception in communication between nodes " + e.getMessage() + " -> handleFailure");
                }
            }
            //here all the files should be checked, so a thread can be started to check for updated in the file DIR
            fileCheckerThread.start();

        } else {
            System.out.println("Fault with directory : " + FILES_DIR);
        }
    }


    /**
     * Method fileAdded
     * Gets called from FileChecker thread when file gets added to FILES_DIR
     *
     * @param filename name of file that is added
     */
    public void fileAdded(String filename){

        //should do almost the same as start()  method

    }















    /**
     * Method sendFile
     * Sends a file to a node
     * @param ip ip of node to send to
     * @param file file to send to node
     * @return response of the node
     */
    public ResponseEntity<String> sendFile(String ip ,MultipartFile file)  {


        final String uri = "http://"+ip+":"+NNConf.NAMINGNODE_PORT+"/node/file";

        // Create headers for multipart form-data
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Create a MultiValueMap to hold the file data
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());  // Use the file's resource directly

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
     *
     * @param filename name of file to look for
     * @return ResponsEntity with ok and file if file is found, otherwise a ResponseStatusException
     */
    public ResponseEntity<Resource> getFile(String filename)  {

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

    public ResponseEntity<String> putFile(MultipartFile file)  {

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

            return ResponseEntity.ok("File uploaded successfully: " + fileName);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload the file");
        }
    }










}
