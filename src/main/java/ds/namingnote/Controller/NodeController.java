package ds.namingnote.Controller;

import ds.namingnote.Service.NodeService;
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
    Logger logger = Logger.getLogger(NodeController.class.getName());


    @Autowired
    public NodeController(NodeService nodeService){
        this.nodeService = nodeService;
    }



    @GetMapping("/file/{filename}")
    public ResponseEntity<Resource> returnFile(@PathVariable String filename) throws FileNotFoundException {

        return nodeService.getFile(filename);

    }


    @PostMapping("/file")
    public ResponseEntity<String> downloadFile(@RequestParam("file") MultipartFile file)  {

        return nodeService.putFile(file);
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
