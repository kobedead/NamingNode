package ds.namingnote.Service;

import ds.namingnote.Agents.FileInfo;
import ds.namingnote.Agents.SyncAgent;
import ds.namingnote.Config.NNConf;
import ds.namingnote.CustomMaps.*;
import ds.namingnote.FileCheck.FileChecker;
import ds.namingnote.Utilities.ReferenceDTO;
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
import java.util.Objects;

import static ds.namingnote.Config.NNConf.*;

@Service
public class ReplicationService {


    private Thread fileCheckerThread = null;

    @Autowired
    private SyncAgent syncAgent ;
    private Thread syncAgentThread;
    private GlobalMap globalMap;

    @Autowired
    @Lazy
    private NodeService nodeService;

    public ReplicationService() {
        globalMap = GlobalMap.getInstance();
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
            if (fileCheckerThread == null) {
                System.out.println("Creating new file checker and starting thread");

                fileCheckerThread = new Thread(new FileChecker(this));
                fileCheckerThread.start();

                this.syncAgent = new SyncAgent();
                syncAgent.initialize(nodeService);
                syncAgentThread = new Thread(syncAgent);
                syncAgentThread.start();


            }

            


        } else {
            System.out.println("Fault with directory : " + FILES_DIR);
        }
    }


    /**
     * Method fileAdded
     * Will check with naming server if file belongs to itself or if it needs replication
     * Will also preform the replication through sendFile
     * @param file  file that is added -> is always a local file (can be replicate through FileChecker)!!
     */
    public void fileAdded(File file){


        String mapping = "/namingserver/node/by-filename/" + file.getName();
        String uri = "http://"+NAMINGSERVER_HOST+":"+ NNConf.NAMINGSERVER_PORT +mapping;

        RestTemplate restTemplate = new RestTemplate();

        try {

            //get ip of node that file belongs to from the naming server
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, null, String.class);
            String ipOfNode = response.getBody(); // the response should contain the ip of the node the file belongs to

            //get own IP
            InetAddress localHost = InetAddress.getLocalHost();           // (MAYBE GET FROM NODESERVICE?)

            //if ip it needs to be sent to is not this node
            if (!ipOfNode.equals(localHost.getHostAddress())) {

                if(globalMap.containsKey(file.getName())){
                    FileInfo fileInfo = globalMap.get(file.getName());
                    if(fileInfo.containsAsReference(ipOfNode)){
                        //if this is the case the node where we want to send the file already has the file
                        //-> we can skip this
                        return;
                    }
                    if (fileInfo.containsAsReference(nodeService.getCurrentNode().getIP())){
                        //i already have the file -> we can skip
                        //-> FileChecker probably called this method
                        return;
                    }
                }


                //here we only get if the file is owned by us and not send to node yet

                System.out.println("The file : "+ file.getName() + " Needs to be send to : " + ipOfNode);

                //should do check or execution if file transfer not completed!!!!
                ResponseEntity<String> check = sendFile(ipOfNode , file , null);
                System.out.println("Response of node to file transfer : " + check.getStatusCode());

                //save ip to filename (reference)
                globalMap.putReplicationReference(file.getName() , ipOfNode);
                //whoReplicatedMyFiles.putSingle(file.getName() , ipOfNode);

            }
            else
                System.out.println("The file : " + file.getName() + " Is already on right node");

                if (!globalMap.containsKey(file.getName())){
                    //this is the rare case where the file is owned by us and needs to be replicated to us
                    //pure local file!!!!                                    //this is overkill
                    globalMap.setOwner(file.getName() ,nodeService.getCurrentNode().getIP());
                }
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

        if (globalMap.containsKey(file.getName())){
            FileInfo info =  globalMap.get(file.getName());
            if (info.isLocked())
                System.out.println("Sending file : "+ file.getName() +" canceled : Lock is active on File from node : "+  info.getLockedByNodeIp());
        }else
            System.out.println("File to send not yet in global map , File:" + file.getName() + "  COULD BE BAD") ;



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

        if (globalMap.containsKey(filename)){
            FileInfo info =  globalMap.get(filename);
            if (info.isLocked())
                System.out.println("Getting of file : "+ filename +" Canceled : Lock is active on File from node : "+  info.getLockedByNodeIp());
        }else
            System.out.println("Getting of file :" + filename + " File not found in globalMap -> CHECK THIS");

        try {
            File file = path.toFile();
            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));

            // Try to determine the content type dynamically
            String contentType = Files.probeContentType(path);
            if (contentType == null) {
                contentType = "application/octet-stream";  // Default fallback
            }

            //add ip of the requester to references
            globalMap.putReplicationReference(file.getName() , ipOfRequester);
            //whoReplicatedMyFiles.putSingle(filename,ipOfRequester);

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


        //this is for the failureAgent

        //if the referenceIp is the ip of this node -> this node should become the new owner of the file
        if(Objects.equals(ipOfRefrence, nodeService.getCurrentNode().getIP())){
            //check if this node already has replicated the file
            //update the FileInfo in global map !!!
        }


        //this is for shutdown purposes

        if (globalMap.containsKey(file.getName())){
            FileInfo fileInfo = globalMap.get(file.getName());
            if (fileInfo.containsAsReference(nodeService.getCurrentNode().getIP())){
                //I already own a replicate of the file nothing
                return ResponseEntity.status(HttpStatus.CONTINUE)
                        .body("I have a replicate already");
            }
            if (Objects.equals(fileInfo.getOwner(), nodeService.getCurrentNode().getIP())){
                //here this node has the file locally -> send to previous again
                sendFile(nodeService.getPreviousNode().getIP(), (File) file,  nodeService.getCurrentNode().getIP());
                return ResponseEntity.status(HttpStatus.CONTINUE)
                        .body("I have send the file to my previous also");

            }

        }


        ////////////put the file in this nodes local dir

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
            globalMap.putReplicationReference(fileName , nodeService.getCurrentNode().getIP());
            //filesIReplicated.putSingle(fileName , ipOfRefrence);


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

                if (globalMap.containsKey(child.getName())){
                    FileInfo fileInfo = globalMap.get(child.getName());

                    //i replicated the file -> send file to previous node (owner should be synced already on previous node)
                    if (fileInfo.containsAsReference(nodeService.getCurrentNode().getIP())){
                        sendFile(nodeService.getPreviousNode().getIP(), child, null);
                        globalMap.removeReplicationReference(child.getName() , nodeService.getCurrentNode().getIP());
                    }
                    //should not be both possible
                    else if (Objects.equals(fileInfo.getOwner(), nodeService.getCurrentNode().getIP())
                            && !fileInfo.getReplicationLocations().isEmpty() ) {
                        //the file is owned by this node and there are replications -> new owner chosen??
                        //for now we will just remove the owner from the FileInfo
                        globalMap.setOwner(child.getName() , null);     //option 2
                    }
                    else { //the file is only local to me IG -> can be removed with shutdown i think
                        globalMap.remove(child.getName());
                        continue;
                    }
                }

            }

        } else {
            System.out.println("Fault with directory : " + FILES_DIR);
        }

        //we first need to make sure that the global file is synced with at least 1 node i think
        //syncAgent.forceSync; -> CHECK!!!!
        globalMap.deleteJsonFile();

    }


    //these aren't actually necessary anymore cause we will sync the map -> maybe for faster propagation this could be usefull tho

/*
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

*/

}




