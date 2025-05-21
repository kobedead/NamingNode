package ds.namingnote.Agents;

import ds.namingnote.CustomMaps.GlobalMap;
import ds.namingnote.Service.NodeService;
import ds.namingnote.Service.ReplicationService;
import ds.namingnote.Utilities.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;


import java.util.*;
import java.util.logging.Logger;
import ds.namingnote.Config.NNConf; // For ports and paths

import static ds.namingnote.Config.NNConf.FILES_DIR;

@Component
public class SyncAgent implements Runnable {

    //idk if this agent needs Serializable cause it isn't passed around the network like the failure agent


    private static final Logger logger = Logger.getLogger(SyncAgent.class.getName());

    private  Node attachedNode;
    private  RestTemplate restTemplate;
    private GlobalMap globalMap;


    @Autowired
    private  NodeService nodeService;

    private long syncIntervalMillis = 30000; // e.g., 30 seconds

    // Constructor used when creating the agent locally
    public SyncAgent() {
        // Fields like nodeService, globalFileList, filesDir, restTemplate
        // will be set by the NodeService or an AgentManager after creation or deserialization.
    }

    public void initialize(Node currentNode) {
        this.attachedNode = currentNode;
        this.globalMap = GlobalMap.getInstance();
        this.restTemplate = new RestTemplate();
    }


    @Override
    public void run() {
        //method to run on separate thread

        //check
        if ( globalMap == null || FILES_DIR == null || restTemplate == null) {
            logger.severe("SyncAgent not properly initialized. Terminating run.");
            return;
        }


        logger.info("SyncAgent started for node: " + attachedNode.getIP());
        try {
            //gets run every interval
            while (!Thread.currentThread().isInterrupted()) {
                logger.fine("SyncAgent run loop iteration for node " + attachedNode.getIP());

                //the globalMap is a singleton storage shared with ReplicationService
                //this means that scanning inside of this agent is unnecessary
                //ReplicationService + FileChecker will update automatically

                // Our only task is to sync these globalMaps


                // Synchronize with the next node
                synchronizeWithNextNode();



                Thread.sleep(syncIntervalMillis);
            }
        } catch (InterruptedException e) {
            logger.info("SyncAgent interrupted for node " + attachedNode.getIP());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
        logger.severe("Error in SyncAgent run loop for node " + attachedNode.getIP() + ": " + e.getMessage());
            e.printStackTrace(); // For more details during development
        }
        logger.info("SyncAgent stopped for node: " + attachedNode.getIP());
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

            //using globalMap might be bad cause of the fact that this is a singleton????
            ResponseEntity<GlobalMap> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, null, GlobalMap.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, FileInfo> nextNodeFileList = (Map<String, FileInfo>) response.getBody();
                logger.fine("SyncAgent: Received file list from next node " + nextNode.getID() + " with " + nextNodeFileList.size() + " entries.");
                globalMap.mergeFileLists(nextNodeFileList);
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
        FileInfo fileInfo = globalMap.get(filename);

        if (fileInfo == null) {
            logger.warning("SyncAgent: Lock request for non-existent file '" + filename + "' in global list.");
            return false; // File not known
        }

        //everyone can ask for a lock i think
        //better would be to implement requestLock in the custom GlobalMap -> concurrency

        synchronized (fileInfo) { // Synchronize on the specific file's info object
            if (!fileInfo.isLocked()) {
                fileInfo.setLocked(true);
                fileInfo.setLockedByNodeIp(requesterNodeIp);
                fileInfo.updateVersion(); // Update version to signify change
                logger.info("SyncAgent: File '" + filename + "' locked by node " + requesterNodeIp + " on owner " + currentNode.getIP());
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
        FileInfo fileInfo = globalMap.get(filename);

        if (fileInfo == null) {
            logger.warning("SyncAgent: Unlock request for non-existent file '" + filename + "' in global list.");
            return false;
        }

        //everyone can ask for a unlock i think
        //better would be to implement requestUnLock in the custom GlobalMap -> concurrency


        synchronized (fileInfo) {
            if (fileInfo.isLocked() && Objects.equals(fileInfo.getLockedByNodeIp(), requesterNodeIp)) {
                fileInfo.setLocked(false);
                fileInfo.setLockedByNodeIp(null); // Or some indicator of not locked
                fileInfo.updateVersion();
                logger.info("SyncAgent: File '" + filename + "' unlocked by node " + requesterNodeIp + " on owner " + attachedNode.getIP());
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

    public Node getAttachedNode() {
        return attachedNode;
    }

    public Map<String, FileInfo> getGlobalMapData() {
        return globalMap.getGlobalMapData();
    }


}