package ds.namingnote.Agents;

import ds.namingnote.Service.NodeService;
import ds.namingnote.Service.ReplicationService;
import ds.namingnote.Utilities.Node;
import ds.namingnote.Agents.FileInfo;

import java.io.File; // For checking local file existence
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import ds.namingnote.Config.NNConf; // For FILES_DIR

public class FailureAgent implements Runnable, Serializable {
    private static final long serialVersionUID = 3L;
    private static final Logger logger = Logger.getLogger(FailureAgent.class.getName());

    private final int failingNodeId;
    private final String failingNodeIp; // Good to have for logs/checks
    private final int newOwnerNodeId;   // The node that will take ownership (e.g., previous of failed)
    private final String newOwnerNodeIp;
    private final int originatorNodeId; // Node that started this agent's journey

    // Transient fields, to be set by the execution environment on each node
    private transient NodeService nodeService;
    private transient ReplicationService replicationService;
    private transient ConcurrentHashMap<String, FileInfo> globalFileList;
    private transient String filesDir; // e.g. NNConf.FILES_DIR

    public FailureAgent(int failingNodeId, String failingNodeIp, int newOwnerNodeId, String newOwnerNodeIp, int originatorNodeId) {
        this.failingNodeId = failingNodeId;
        this.failingNodeIp = failingNodeIp; // Store for clarity
        this.newOwnerNodeId = newOwnerNodeId;
        this.newOwnerNodeIp = newOwnerNodeIp; // Store for clarity
        this.originatorNodeId = originatorNodeId;
    }

    public void initialize(NodeService nodeService, ReplicationService replicationService, ConcurrentHashMap<String, FileInfo> globalFileList, String filesDir) {
        this.nodeService = nodeService;
        this.replicationService = replicationService;
        this.globalFileList = globalFileList;
        this.filesDir = filesDir;
    }

    public int getOriginatorNodeId() {
        return originatorNodeId;
    }

    public int getFailingNodeId() {
        return failingNodeId;
    }

    @Override
    public void run() {
        if (nodeService == null || globalFileList == null || replicationService == null || filesDir == null) {
            logger.severe("FailureAgent not properly initialized on node " + (nodeService != null ? nodeService.getCurrentNode().getID() : "UNKNOWN") + ". Terminating run.");
            return;
        }

        Node currentNode = nodeService.getCurrentNode();
        logger.info("FailureAgent running on node " + currentNode.getID() +
                " (Originator: " + originatorNodeId + "). Handling failure of: " + failingNodeId +
                ". New owner: " + newOwnerNodeId);

        List<String> filesToTransferToNewOwner = new ArrayList<>();

        for (Map.Entry<String, FileInfo> entry : globalFileList.entrySet()) {
            String filename = entry.getKey();
            FileInfo fileInfo = entry.getValue();

            if (fileInfo.getOwnerNodeId() == failingNodeId) {
                logger.info("Node " + currentNode.getID() + ": File '" + filename +
                        "' was owned by failed node " + failingNodeId +
                        ". Updating owner to " + newOwnerNodeId + " (" + newOwnerNodeIp + ").");

                fileInfo.setOwnerNodeId(newOwnerNodeId);
                fileInfo.setOwnerNodeIp(newOwnerNodeIp);
                fileInfo.setLocked(false); // Unlock files owned by the failed node
                fileInfo.setLockedByNodeId(0);
                fileInfo.incrementVersion(); // Signify change

                // If the current node IS the new owner, it needs to ensure it has the file.
                if (currentNode.getID() == newOwnerNodeId) {
                    filesToTransferToNewOwner.add(filename);
                }
            }
        }

        // Handle file transfers if this node is the new owner
        if (currentNode.getID() == newOwnerNodeId) {
            ensureFilesPresent(filesToTransferToNewOwner);
        }

        // The globalFileList on this node is now updated. SyncAgent will propagate changes.
        logger.info("FailureAgent finished processing on node " + currentNode.getID() + ". File list updated.");
    }

    private void ensureFilesPresent(List<String> filenames) {
        Node currentNode = nodeService.getCurrentNode(); // Should be the newOwnerNode
        logger.info("Node " + currentNode.getID() + " is the new owner. Checking/transferring " + filenames.size() + " files.");

        for (String filename : filenames) {
            File localFile = new File(filesDir, filename);
            if (!localFile.exists()) {
                logger.info("Node " + currentNode.getID() + ": File '" + filename +
                        "' not found locally. Attempting to acquire from other nodes (replicas).");

                // How to get the file?
                // 1. The failed node is gone.
                // 2. A replica should exist on the FAILED NODE'S PREVIOUS node.
                //    If `newOwnerNodeId` IS the failed node's previous, it might already have it.
                //    This logic needs to align with your replication strategy.
                //    Let's assume `ReplicationService` has a method to find and download a file.
                //    This part is complex and depends heavily on how your `ReplicationService.getOwnerAndDownload()`
                //    or similar function works when the primary owner (failed node) is gone.
                //    It might need to query the Naming Server for replica locations or try known replica holders.

                // Simplification: For this lab, the spec says "transfer... if the new owner doesn’t have a copy".
                // It doesn't explicitly say *how* the FailureAgent triggers this transfer beyond updating logs.
                // A practical approach: the new owner, after its FileInfo is updated, will need to fetch it.
                // This could be a separate process or triggered by the SyncAgent noticing it "owns" a file it doesn't have.

                // For now, let's just log. A robust implementation would use ReplicationService
                // to find a replica and download. The ReplicationService would need to be aware
                // that the original owner (failingNodeId) is gone.
                logger.log(Level.INFO, "Node " + currentNode.getID() + ": Needs to download '" + filename +
                        "'. Current ReplicationService might need enhancement for this scenario.");
                // Example of what might be called:
                // replicationService.acquireFileAsNewOwner(filename, failingNodeId);
            } else {
                logger.info("Node " + currentNode.getID() + ": File '" + filename + "' already present locally.");
            }
            // Update local log file as per spec (e.g., simple text log)
            // For example, using a dedicated logger or appending to a file:
            // LogManager.getLog("FailureRecoveryLog").info("File " + filename + " now owned by " + newOwnerNodeId);
        }
    }
}