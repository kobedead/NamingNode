package ds.namingnote.Controller;

import ds.namingnote.Service.NodeService;
import ds.namingnote.Service.ReplicationService;
import ds.namingnote.Utilities.Node;
import ds.namingnote.Agents.FailureAgent;
import ds.namingnote.Agents.SyncAgent; // Only if controller interacts directly
import ds.namingnote.Agents.FileInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ds.namingnote.Config.NNConf; // For FILES_DIR
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ds.namingnote.Config.NNConf.FILES_DIR;


@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final Logger logger = Logger.getLogger(AgentController.class.getName());


    @Autowired
    private SyncAgent syncAgent;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private ReplicationService replicationService;


    public AgentController(SyncAgent syncAgent) {
        this.syncAgent = syncAgent;
    }


    /**
     * Endpoint for SyncAgents to get the file list from this node.
     */
    @GetMapping("/sync/filelist")
    public ResponseEntity<Map<String, FileInfo>> getFilelist() {
        logger.fine("Node " + nodeService.getCurrentNode().getID() + " responding to /sync/filelist request. List size: " + syncAgent.getGlobalMapData().size());
        return ResponseEntity.ok(syncAgent.getGlobalMapData());
    }


    @PostMapping("/sync/forward-filelist/{ipoforigin}")
    public ResponseEntity<String> forwardReceivedFilelist(@PathVariable String ipoforigin , @RequestBody Map<String, FileInfo> receivedMap) {
        String currentNodeIp = syncAgent.getAttachedNode().getIP();
        if (Objects.equals(ipoforigin, currentNodeIp)) {
            logger.info("Node " + currentNodeIp + ": Forwarding chain from " + ipoforigin + " has reached ME (originator). Final merge.");
            syncAgent.mergeGlobalMap(receivedMap); // Assuming this just merges and saves
            return ResponseEntity.ok("Forwarding chain reached originator. Map merged.");
        } else {
            return syncAgent.forwardMap(receivedMap, ipoforigin);
        }    }

    /**
     * Endpoint to request a lock on a file.
     * The node receiving this request should be the OWNER of the file.
     */
    @PostMapping("/sync/lock/{filename}")
    public ResponseEntity<String> requestLock(@PathVariable String filename, @RequestParam("requesterNodeIp") String requesterNodeIp) {
        Node currentNode = syncAgent.getAttachedNode();
        if (currentNode == null) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Node not ready");

        logger.info("Node " + currentNode.getIP() + " received lock request for '" + filename + "' from node " + requesterNodeIp);

        boolean success = syncAgent.requestLock(filename, requesterNodeIp);

        if (success) {
            return ResponseEntity.ok("File '" + filename + "' locked by " + requesterNodeIp);
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Lock request for '" + filename + "' denied (e.g., already locked or file unknown).");
        }
    }

    /**
     * Endpoint to release a lock on a file.
     * The node receiving this request should be the OWNER of the file.
     */
    @PostMapping("/sync/unlock/{filename}")
    public ResponseEntity<String> releaseLock(@PathVariable String filename, @RequestParam("requesterNodeIp") String requesterNodeIP) {
        Node currentNode = syncAgent.getAttachedNode();
        if (currentNode == null) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Node not ready");

        logger.info("Node " + currentNode.getID() + " received unlock request for '" + filename + "' from node " + requesterNodeIP);

        boolean success = syncAgent.releaseLock(filename, requesterNodeIP);

        if (success) {
            return ResponseEntity.ok("File '" + filename + "' unlocked by " + requesterNodeIP);
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Unlock request for '" + filename + "' denied (e.g., not locked by requester or file unknown).");
        }
    }

    /**
     * Receives a serialized agent, executes it, and forwards if necessary.
     * As per spec:
     * 1. Receives an agent as a parameter
     * 2. Creates a thread from a received agent
     * 3. Starts the thread
     * 4. Waits until the thread is finished
     * 5. Executes the REST method on the next node (unless the agent needs to be terminated)
     */
    @PostMapping("/execute")
    public ResponseEntity<String> executeFailureAgent(@RequestBody byte[] serializedAgent)  {
        return nodeService.executeFailureAgent(serializedAgent);
    }


}


















