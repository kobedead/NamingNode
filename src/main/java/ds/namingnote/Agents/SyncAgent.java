package ds.namingnote.Agents;

import ds.namingnote.CustomMaps.GlobalMapList;
import ds.namingnote.FileCheck.FileChecker;
import ds.namingnote.FileCheck.FileEvent;
import ds.namingnote.Service.NodeService;
import ds.namingnote.Utilities.Node;
import ds.namingnote.Utilities.ReferenceDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;


import java.io.File;
import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import ds.namingnote.Config.NNConf; // For ports and paths

import static ds.namingnote.Config.NNConf.FILES_DIR;
import static ds.namingnote.Config.NNConf.NAMINGSERVER_HOST;

@Component
public class SyncAgent implements Runnable, Serializable {

    //idk if this agent needs Serializable cause it isn't passed around the network like the failure agent

    @Serial
    private static final long serialVersionUID = 2L;
    private static final Logger logger = Logger.getLogger(SyncAgent.class.getName());

    // These will be null when serialized. They need to be re-initialized/set on the target node.
    // Option 1: Pass IDs/IPs and look up services on the target node.
    // Option 2: Have a static context accessor (generally discouraged but can work).
    // Option 3: The receiving REST endpoint injects them before starting the thread. (Preferred for this setup)

    private transient Node attachedNode;

    private transient GlobalMapList globalFileList; // The node's actual list
    private transient RestTemplate restTemplate;


    //these blockingqueue of fileEvents will get updated from other thread when needed -> check in run interval
    private BlockingQueue<ReferenceDTO> localFiles = new LinkedBlockingQueue<>();
    private BlockingQueue<ReferenceDTO> filesIreplicated = new LinkedBlockingQueue<>();
    private BlockingQueue<ReferenceDTO> whoHasMyFiles = new LinkedBlockingQueue<>();

    @Autowired
    private  NodeService nodeService; // 'transient' so not serialized



    private long syncIntervalMillis = 30000; // e.g., 30 seconds

    // Constructor used when creating the agent locally
    public SyncAgent() {
        // Fields like nodeService, globalFileList, filesDir, restTemplate
        // will be set by the NodeService or an AgentManager after creation or deserialization.
    }

    public void initialize() {

        this.attachedNode = nodeService.getCurrentNode();
        this.restTemplate = new RestTemplate();
        CheckfileAtBegin();

    }





    @Override
    public void run() {
        //method to run on separate thread

        //check
        if (nodeService == null || globalFileList == null || FILES_DIR == null || restTemplate == null) {
            logger.severe("SyncAgent not properly initialized. Terminating run.");
            return;
        }


        logger.info("SyncAgent started for node: " + nodeService.getCurrentNode().getID());
        try {
            //gets run every interval
            while (!Thread.currentThread().isInterrupted()) {
                logger.fine("SyncAgent run loop iteration for node " + nodeService.getCurrentNode().getID());

                //we can let the agent check the files in dir every 30s to check if there are new files

                //we will look at the beginning through the files and add them to the agent
                //then we run the agent and let the checking of the files to the FileChecker that also notifies the
                //replication service
                //We let the FileChecker notify the replicationService, then the replicationService will update
                //the filesIreplicated and whoHasMyFiles
                //this i will check here to update globally synct map
                //with this the global map will also know the references kept by replication service and can check this
                //this is needed (i think) because if a file gets added to the directory is this file a local file
                //or a replicated file???


                //so first go over all local files and add them to gobalFileList

                for (ReferenceDTO referenceDTO : filesIreplicated) {
                    globalFileList.put(referenceDTO.getFileName(),new FileInfo(referenceDTO.getFileName(), null, attachedNode.getIP()));
                }
                for (ReferenceDTO referenceDTO : whoHasMyFiles) {
                    globalFileList.put(referenceDTO.getFileName(),new FileInfo(referenceDTO.getFileName(), attachedNode.getIP(), referenceDTO.getIpOfRefrence()));
                }
                //these are the pure local files, that don't get replicated immediately
                for (ReferenceDTO referenceDTO : localFiles){
                    globalFileList.put(referenceDTO.getFileName() , new FileInfo(referenceDTO.getFileName(), attachedNode.getIP(), null));
                }

                //now the agent should have a full view of the files on the attached node -> we can start the sync

                // Synchronize with the next node
                synchronizeWithNextNode();

                //we also need some extra logic to help with the locking and unlocking of the files
                //check lock or unlock when someone asks for the download of the file!!!!
                //so the GlobalFileList should be shared with replicationService i think


                Thread.sleep(syncIntervalMillis);
            }
        } catch (InterruptedException e) {
            logger.info("SyncAgent interrupted for node " + nodeService.getCurrentNode().getID());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.severe("Error in SyncAgent run loop for node " + nodeService.getCurrentNode().getID() + ": " + e.getMessage());
            e.printStackTrace(); // For more details during development
        }
        logger.info("SyncAgent stopped for node: " + nodeService.getCurrentNode().getID());
    }

    private void CheckfileAtBegin() {
        File dir = new File(FILES_DIR);
        File[] localFiles = dir.listFiles();
        if (localFiles != null) {

            for (File localFile : localFiles) {
                if (localFile.isFile()) {
                    String filename = localFile.getName();
                    globalFileList.put(filename, new FileInfo(filename , attachedNode.getIP() , null));
                }
            }
        }

    }

    private void synchronizeWithNextNode() {
        Node nextNode = nodeService.getNextNode();

        if (nextNode == null || nextNode.getID() == attachedNode.getID()) {
            logger.fine("SyncAgent: No next node or only node in network. Skipping sync with next.");
            return;
        }

        try {
            String url = "http://" + nextNode.getIP() + ":" + NNConf.NAMINGNODE_PORT + "/agent/sync/filelist";
            logger.fine("SyncAgent: Requesting file list from next node: " + url);

            ResponseEntity<GlobalMapList> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, null, GlobalMapList.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, FileInfo> nextNodeFileList = response.getBody();
                logger.fine("SyncAgent: Received file list from next node " + nextNode.getID() + " with " + nextNodeFileList.size() + " entries.");
                globalFileList.mergeFileLists(nextNodeFileList);
            } else {
                logger.warning("SyncAgent: Failed to get file list from next node " + nextNode.getID() +
                        ". Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.warning("SyncAgent: Error synchronizing with next node " + nextNode.getIP() + ": " + e.getMessage());
            // Consider implications: if next node is down, failure handling should eventually kick in.
        }
    }


    // --- Public methods for locking/unlocking (called by NodeService/Controller) ---

    public boolean requestLock(String filename, String requesterNodeIp) {
        Node currentNode = nodeService.getCurrentNode();
        FileInfo fileInfo = globalFileList.get(filename);

        if (fileInfo == null) {
            logger.warning("SyncAgent: Lock request for non-existent file '" + filename + "' in global list.");
            return false; // File not known
        }

        //everyone can ask for a lock i think

        synchronized (fileInfo) { // Synchronize on the specific file's info object
            if (!fileInfo.isLocked()) {
                fileInfo.setLocked(true);
                fileInfo.setLockedByNodeIp(requesterNodeIp);
                fileInfo.updateVersion(); // Update version to signify change
                logger.info("SyncAgent: File '" + filename + "' locked by node " + requesterNodeIp + " on owner " + currentNode.getID());
                // This change will be propagated in the next sync cycle.
                // For faster propagation, could immediately push this update to neighbors.
                //CHECK
                return true;
            } else if (Objects.equals(fileInfo.getLockedByNodeIp(), requesterNodeIp)) {
                logger.info("SyncAgent: File '" + filename + "' already locked by requester " + requesterNodeIp);
                return true; // Already locked by the requester
            } else {
                logger.warning("SyncAgent: File '" + filename + "' already locked by node " + fileInfo.getLockedByNodeIp() + ". Lock request from " + requesterNodeIp + " denied.");
                return false; // Locked by someone else
            }
        }
    }

    public boolean releaseLock(String filename, String requesterNodeIp) {
        Node currentNode = nodeService.getCurrentNode();
        FileInfo fileInfo = globalFileList.get(filename);

        if (fileInfo == null) {
            logger.warning("SyncAgent: Unlock request for non-existent file '" + filename + "' in global list.");
            return false;
        }

        if (!Objects.equals(fileInfo.getOwner(), currentNode.getIP())) {
            logger.warning("SyncAgent: Unlock request for file '" + filename + "' received by non-owner node " + currentNode.getID());
            return false;
        }
        synchronized (fileInfo) {
            if (fileInfo.isLocked() && Objects.equals(fileInfo.getLockedByNodeIp(), requesterNodeIp)) {
                fileInfo.setLocked(false);
                fileInfo.setLockedByNodeIp(null); // Or some indicator of not locked
                fileInfo.updateVersion();
                logger.info("SyncAgent: File '" + filename + "' unlocked by node " + requesterNodeIp + " on owner " + currentNode.getID());
                // Propagated in next sync cycle
                //CHECK
                return true;
            } else if (!fileInfo.isLocked()) {
                logger.info("SyncAgent: File '" + filename + "' was already unlocked.");
                return true; // Already unlocked
            } else {
                logger.warning("SyncAgent: File '" + filename + "' locked by " + fileInfo.getLockedByNodeIp() + ". Unlock request from " + requesterNodeIp + " denied.");
                return false; // Locked by someone else or not locked by requester
            }
        }
    }
    public void setSyncIntervalMillis(long interval) {
        this.syncIntervalMillis = interval;
    }







    /**
     * Called from FileChecker when a file event occurs - Should be thread save like this
     * @param reference The FileInfo object
     */
    public void addLocalFiles (ReferenceDTO reference) {
        // offer() is generally preferred over put() in this scenario
        // unless you want the FileChecker to block if the queue is full.
        // For WatchService, blocking might be undesirable.
        localFiles.offer(reference);
        // Or if you want to ensure the event is added, potentially blocking:
        // try { fileEventQueue.put(event); } catch (InterruptedException e) { /* Handle or log */ }
    }

    /**
     * Called from replicationService(MAP) when a file event occurs - Should be thread save like this
     * @param reference The FileEvent object
     */
    public void addFileIReplicated(ReferenceDTO reference) {
        // offer() is generally preferred over put() in this scenario
        // unless you want the FileChecker to block if the queue is full.
        // For WatchService, blocking might be undesirable.
        filesIreplicated.offer(reference);
        // Or if you want to ensure the event is added, potentially blocking:
        // try { fileEventQueue.put(event); } catch (InterruptedException e) { /* Handle or log */ }
    }


    /**
     * Called from replicationService(MAP) when a file event occurs - Should be thread save like this
     * @param reference The FileEvent object
     */
    public void addWhoHasMyFile(ReferenceDTO reference) {
        // offer() is generally preferred over put() in this scenario
        // unless you want the FileChecker to block if the queue is full.
        // For WatchService, blocking might be undesirable.
        whoHasMyFiles.offer(reference);
        // Or if you want to ensure the event is added, potentially blocking:
        // try { fileEventQueue.put(event); } catch (InterruptedException e) { /* Handle or log */ }
    }


    public Node getAttachedNode() {
        return attachedNode;
    }

    public GlobalMapList getGlobalFileList() {
        return globalFileList;
    }
}