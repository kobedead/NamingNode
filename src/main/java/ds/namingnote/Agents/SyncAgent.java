package ds.namingnote.Agents;

import ds.namingnote.FileCheck.FileChecker;
import ds.namingnote.Multicast.MulticastSender;
import ds.namingnote.Service.NodeService;
import ds.namingnote.Utilities.Node;
import ds.namingnote.model.LocalFile;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static ds.namingnote.Config.NNConf.FILES_DIR;

public class SyncAgent implements Runnable, Serializable {

    private static final Logger LOGGER = Logger.getLogger(SyncAgent.class.getName());

    private List<LocalFile> ownedFiles;
    private final INodeCommunicator nodeCommunicator; // To communicate with other nodes

    private final NodeService nodeService;
    private final MulticastSender multicastSender;

    public SyncAgent(NodeService nodeService, MulticastSender multicastSender) {
        this.ownedFiles = new ArrayList<>();
        this.nodeCommunicator = new NodeCommunicator();
        this.nodeService = nodeService;
        this.multicastSender = multicastSender;
    }

    @Override
    public void run() {
        while (true) { // Sync agent runs infinitely [cite: 19]
            try {
                synchronizeFiles();
                Thread.sleep(5000); // Sleep for 5 seconds before next sync
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void synchronizeFiles() {
        updateOwnedFiles();
        syncWithNextNode();
        handleLockRequests();
    }

    private void updateOwnedFiles() {
        this.ownedFiles.clear();
        File dir = new File(FILES_DIR);
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            for (File child : directoryListing) {
                // Assumes files are not locked when creating
                this.ownedFiles.add(new LocalFile(child.getName(), false));
                LOGGER.info("updateOwnedFiles -> Added file: " + child.getName());
            }
        } else {
            System.out.println("Fault with directory : " + FILES_DIR);
        }
    }

    private void syncWithNextNode() {
        Node nextNode = nodeService.getNextNode();
        List<LocalFile> nextNodeFiles = nodeCommunicator.requestFileList(nextNode);

        // Update networkFiles (the agent's list) based on nextNodeFiles
        updateNetworkFileList(nextNodeFiles);
    }

    private void updateNetworkFileList(List<LocalFile> nextNodeFiles) {
        // Logic to update this node's view of all files in the network

        // TODO: for now just adds new files from the next node if they are not present, check if this is correct
        ownedFiles.addAll(nextNodeFiles);
        this.ownedFiles = this.ownedFiles.stream().distinct().collect(Collectors.toList());
    }

    private void handleLockRequests() {
        // Might be unnecessary
    }

    public void lockFile(String filename) {
        for (LocalFile file : this.ownedFiles) {
            if (file.getFileName().equals(filename)) {
                file.setLocked(true);
                updateNetworkFileList();
                return;
            }
        }
    }

    public void unlockFile(String filename) {
        for (LocalFile file : this.ownedFiles) {
            if (file.getFileName().equals(filename)) {
                file.setLocked(false);
                updateNetworkFileList();
                return;
            }
        }
    }

    private void updateNetworkFileList() {
        // Send updated file list and lock status to all other nodes
        nodeCommunicator.broadcastAgentState(this.ownedFiles, this.multicastSender);
    }

    public List<LocalFile> getOwnedFiles() {
        return ownedFiles;
    }
}

