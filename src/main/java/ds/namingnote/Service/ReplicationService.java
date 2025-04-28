package ds.namingnote.Service;

import ds.namingnote.Config.NNConf;
import ds.namingnote.Utilities.Utilities;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static ds.namingnote.Config.NNConf.FILES_DIR;
import static ds.namingnote.Config.NNConf.NAMINGSERVER_HOST;

@Service
public class ReplicationService {

    public void start(){

        File dir = new File(FILES_DIR);
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            for (File child : directoryListing) {

                String mapping = "/node/by-filename/";
                String uri = "http://"+NAMINGSERVER_HOST+":"+ NNConf.NAMINGSERVER_PORT +mapping+child.getName();

                RestTemplate restTemplate = new RestTemplate();

                try {
                    ResponseEntity<String> response = restTemplate.exchange(
                            uri, HttpMethod.POST, null, String.class);
                    String ipOfNode = response.getBody(); // the response should contain the ip of the node the file belongs to

                    InetAddress localHost = InetAddress.getLocalHost(); //get own IP

                    if (ipOfNode.equals(localHost.getHostAddress())) {
                        //this node should own the file, so it doenst need to send it anywhere


                    }else {
                        //this node isn't the right one -> send file to ip and save ip in map

                        //should do check or execution if file transfer not completed!!!!
                        ResponseEntity<String> check = sendFile(localHost.getHostAddress() , (MultipartFile) child);
                        System.out.println("Response of node to file transfer : " + check.getStatusCode());

                        //save ip to filename (refrence)



                    }


                } catch (Exception e) {
                    System.out.println("Exception in communication between nodes " + e.getMessage() + " -> handleFailure");
                }
            }
        } else {
            System.out.println("Fault with directory : " + FILES_DIR);
        }
    }

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





    public ResponseEntity<Resource> getFile(String filename)  {

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










}
