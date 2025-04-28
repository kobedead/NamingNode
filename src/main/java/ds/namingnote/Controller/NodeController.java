package ds.namingnote.Controller;

import ds.namingnote.Service.NodeService;
import ds.namingnote.Service.ReplicationService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.FileNotFoundException;
import java.util.logging.Logger;

@RestController
@RequestMapping("/node")
public class NodeController {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(NodeController.class);
    NodeService nodeService;
    ReplicationService replicationService;
    Logger logger = Logger.getLogger(NodeController.class.getName());


    @Autowired
    public NodeController(NodeService nodeService , ReplicationService replicationService){
        this.nodeService = nodeService;
        this.replicationService = replicationService;
    }



    @GetMapping("/file/{filename}")
    public ResponseEntity<Resource> returnFile(@PathVariable String filename) throws FileNotFoundException {

        return replicationService.getFile(filename);

    }


    @PostMapping("/file")
    public ResponseEntity<String> downloadFile(@RequestParam("file") MultipartFile file)  {

        return replicationService.putFile(file);
    }


    @PostMapping("/id/next/{nextID}")
    public ResponseEntity<String> updateNextID(@PathVariable int nextID)  {
        logger.info("POST: /id/next/" + nextID);
        nodeService.setNextID(nextID);
        return ResponseEntity.ok("NextID updated succesfully");

    }

    @PostMapping("/id/previous/{previousID}")
    public ResponseEntity<String> updatePreviousID(@PathVariable int previousID)  {
        nodeService.setPreviousID(previousID);
        return ResponseEntity.ok("PreviousID updated succesfully");

    }

    /**
     * Method size, get size from map of namingserver.
     * * if namingserver returns -1 -> hash already in map -> cant join
     *
     * @param numberOfNodes the number of nodes in the naming server map
     * @return responseEnity if everything went ok
     */
    @PostMapping("/size")
    public ResponseEntity<String> size(@RequestBody int numberOfNodes) {
        nodeService.calculatePreviousAndNext(numberOfNodes);
        return ResponseEntity.ok("Received numberOfNodes: " + numberOfNodes);
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("alive");
    }


    @DeleteMapping("/shutdown")
    public void shutdown(){

        nodeService.shutdown();

    }




}
