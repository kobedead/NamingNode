package ds.namingnote.Controller;

import ds.namingnote.Service.NodeService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.FileNotFoundException;

@RestController
@RequestMapping("/node")
public class NodeController {

    NodeService nodeService;

    public NodeController(){
        nodeService = new NodeService();
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
}
