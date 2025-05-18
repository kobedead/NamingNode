package ds.namingnote.Controller;

import ds.namingnote.CustomMaps.GlobalMapList;
import ds.namingnote.Service.NodeService;
import ds.namingnote.Service.ReplicationService; // If FailureAgent needs it directly
import ds.namingnote.Utilities.Node;
import ds.namingnote.Agents.FailureAgent;
import ds.namingnote.Agents.SyncAgent; // Only if controller interacts directly
import ds.namingnote.Agents.FileInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate; // For agent's RestTemplate needs
import ds.namingnote.Config.NNConf; // For FILES_DIR

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final Logger logger = Logger.getLogger(AgentController.class.getName());

    @Autowired
    private SyncAgent syncAgent;


    /**
     * Endpoint for SyncAgents to get the file list from this node.
     */
    @GetMapping("/sync/filelist")
    public ResponseEntity<Map<String, FileInfo>> getFilelist() {

        Node currentNode = syncAgent.getAttachedNode();
        if (currentNode == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null); // Node not ready
        }
        logger.fine("Node " + currentNode.getID() + " responding to /sync/filelist request. List size: " + syncAgent.getGlobalFileList().size());
        return ResponseEntity.ok(syncAgent.getGlobalFileList());
    }

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
}



}














