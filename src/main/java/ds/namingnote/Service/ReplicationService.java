package ds.namingnote.Service;

import ds.namingnote.Agents.FileInfo;
import ds.namingnote.Agents.SyncAgent;
import ds.namingnote.Config.NNConf;
import ds.namingnote.CustomMaps.*;
import ds.namingnote.FileCheck.FileChecker;
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
import java.net.UnknownHostException;
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
     */
    public void start(){

        if (fileCheckerThread == null || !fileCheckerThread.isAlive()) {
            System.out.println("Creating new file checker and starting thread");
            fileCheckerThread = new Thread(new FileChecker(this));
            fileCheckerThread.start();
        } else {
            System.out.println("File checker thread is already running.");
        }

        if (syncAgentThread == null || !syncAgentThread.isAlive()) {
            syncAgent.initialize(nodeService);
            globalMap.setSyncAgent(syncAgent);
            System.out.println("Creating new sync agent and starting thread");
            syncAgentThread = new Thread(syncAgent);
            syncAgentThread.start();
        } else {
            System.out.println("Sync agent thread is already running.");
        }

        checkFiles();

    }

    public void checkFiles(){
        File dir = new File(FILES_DIR);  //get files dir
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            //loop over files
            for (File child : directoryListing) {
                fileAdded(child);
            }
            System.out.println("All Files are checked and replicated if needed");
            //here all the files should be checked
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

        System.out.println("File added called");
        String ipOfOwner;
        FileInfo fileInfo = globalMap.get(file.getName());
        if (fileInfo != null){
            ipOfOwner = fileInfo.getOwner();
        }else{
            ipOfOwner = nodeService.getCurrentNode().getIP();
            globalMap.setOwner(file.getName(), ipOfOwner);
            syncAgent.forwardMap(globalMap.getGlobalMapData() , syncAgent.getAttachedNode().getIP());
            //push the change so the node receiving the files knows the owner -> concurrency is needed for proper scaling!
        }

        String mapping = "/namingserver/node/by-filename-owner?filename=" + file.getName() + "&ownerIp=" + ipOfOwner;
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

                if (fileInfo != null) {
                    if (fileInfo.containsAsReference(ipOfNode)) {
                        //if this is the case the node where we want to send the file already has the file
                        //-> we can skip this
                        return;
                    }
                    if (fileInfo.containsAsReference(nodeService.getCurrentNode().getIP())) {
                        //i already have the file -> we can skip
                        //-> FileChecker probably called this method
                        return;
                    }
                }


                //here we only get if the file is owned by us and not send to node yet

                System.out.println("The file : " + file.getName() + " Needs to be send to : " + ipOfNode);

                //should do check or execution if file transfer not completed!!!!
                ResponseEntity<String> check = sendFile(ipOfNode, file, null);
                System.out.println("Response of node to file transfer : " + check.getStatusCode());

                //save ip to filename (reference)
                globalMap.putReplicationReference(file.getName(), ipOfNode);

            } else {
                //shouldnt be possible anymore
                System.out.println("The file : " + file.getName() + " Is already on right node");

                if (!globalMap.containsKey(file.getName())) {
                    System.out.println("Set this node as owner of the file in GlobalMap");
                    globalMap.setOwner(file.getName(), nodeService.getCurrentNode().getIP());
                }
            }
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
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

        System.out.println("Sending file: " + file.getAbsolutePath() + ", exists: " + file.exists() + ", length: " + file.length());



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
            System.out.println("PutFile called, New Owner is assigned. New Owner  : " + ipOfRefrence);
            globalMap.setOwner(file.getOriginalFilename() , ipOfRefrence);
            syncAgent.forwardMap(globalMap.getGlobalMapData() , syncAgent.getAttachedNode().getIP());
            //push the change so the node receiving the files knows the owner -> concurrency is needed for proper scaling!
        }else {

            //this is for shutdown purposes

            if (globalMap.containsKey(file.getOriginalFilename())) {
                FileInfo fileInfo = globalMap.get(file.getOriginalFilename());
                if (fileInfo.containsAsReference(nodeService.getCurrentNode().getIP())) {
                    //I already own a replicate of the file nothing
                    return ResponseEntity.status(HttpStatus.CONTINUE)
                            .body("I have a replicate already");
                }
                if (Objects.equals(fileInfo.getOwner(), nodeService.getCurrentNode().getIP())) {
                    //here this node has the file locally -> send (stream) to previous, this is used cause multipartfile is received
                    final String uri = "http://"+nodeService.getPreviousNode().getIP()+":"+NNConf.NAMINGNODE_PORT+"/node/file/"+nodeService.getCurrentNode().getIP();

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                    try {
                        body.add("file", new InputStreamResource(file.getInputStream(), file.getOriginalFilename()));
                        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
                        RestTemplate restTemplate = new RestTemplate();
                        ResponseEntity<String> response = restTemplate.exchange(
                                uri, HttpMethod.POST, entity, String.class);
                        return response;

                    } catch (IOException e) {
                        e.printStackTrace();
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Error reading input stream of the file.");
                    }
                }
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

    public ResponseEntity<String> putFileFrontend(MultipartFile file) {
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
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload the file");
        }
    }


        public void shutdown(){
        File dir = new File(FILES_DIR);  //get files dir
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            //loop over files and check if replication
            for (File child : directoryListing) {
                //if name of file in replication files
                if (globalMap.containsKey(child.getName())) {
                    FileInfo fileInfo = globalMap.get(child.getName());

                    //should not be both possible
                    if (Objects.equals(fileInfo.getOwner(), nodeService.getCurrentNode().getIP())
                            && !fileInfo.getReplicationLocations().isEmpty()) {
                        System.out.println("the file is owned by this node and there are replications -> new owner is previous node\n");
                        //the file is owned by this node and there are replications -> new owner is previous node
                        globalMap.setOwner(child.getName() , nodeService.getPreviousNode().getIP());
                        syncAgent.forwardMap(globalMap.getGlobalMapData() , nodeService.getPreviousNode().getIP());
                        ResponseEntity<String> check = sendFile(nodeService.getPreviousNode().getIP(), child, nodeService.getPreviousNode().getIP());
                        System.out.println("Response of node to file transfer : " + check.getStatusCode());

                    }
                    //I replicated the file -> send file to previous node
                    else if (fileInfo.containsAsReference(nodeService.getCurrentNode().getIP())) {
                        System.out.println("I replicated the file -> send file to previous node");
                        ResponseEntity<String> check = sendFile(nodeService.getPreviousNode().getIP(), child, null);
                        System.out.println("Response of node to file transfer : " + check.getStatusCode());

                        globalMap.removeReplicationReference(child.getName(), nodeService.getCurrentNode().getIP());
                        //syncAgent.forwardMap(globalMap.getGlobalMapData() , nodeService.getPreviousNode().getIP());

                    }

                    else { //the file is only local to me IG -> can be removed with shutdown
                        globalMap.remove(child.getName());
                    }
                }

                boolean deleted = child.delete();
                if (deleted) {
                    System.out.println("File " + child.getName() + " deleted successfully.");
                } else {
                    System.err.println("Failed to delete file " + child.getName());
                    // Optionally handle the failure to delete (e.g., log it)
                }
            }
        } else {
            System.out.println("Fault with directory : " + FILES_DIR);
        }
            syncAgent.forwardMap(globalMap.getGlobalMapData() , nodeService.getPreviousNode().getIP());
            globalMap.deleteJsonFile();
    }


    public void interruptSyncAgent() throws InterruptedException {
        syncAgentThread.interrupt();
    }
    public void interruptFileChecker() throws InterruptedException {
        fileCheckerThread.interrupt();
    }

    public void forwardMap(){
        syncAgent.forwardMap(globalMap.getGlobalMapData() ,nodeService.getCurrentNode().getIP() );
    }


}




