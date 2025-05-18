package ds.namingnote.Controller;

import ds.namingnote.Service.NodeService;
import ds.namingnote.Service.ReplicationService;
import ds.namingnote.Utilities.Node;
import ds.namingnote.Utilities.ReferenceDTO;
import ds.namingnote.model.LocalFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@RestController
@RequestMapping("/node")
public class NodeController {

    NodeService nodeService;
    ReplicationService replicationService;
    Logger logger = Logger.getLogger(NodeController.class.getName());


    @Autowired
    public NodeController(NodeService nodeService , ReplicationService replicationService){
        this.nodeService = nodeService;
        this.replicationService = replicationService;
    }



    @GetMapping("/file/{filename}")
    public ResponseEntity<Resource> returnFile(@PathVariable String filename, HttpServletRequest request) {
        return replicationService.getFile(filename, request.getRemoteAddr()); // get the file
    }

    /**
     * This mapping will put a file to local storage and use the sender ip as
     * reference of where the file came from
     *
     * @param file file to save to node
     * @param request request to get senders ip
     * @return
     */
    @PostMapping("/file")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file , HttpServletRequest request)  {
        return replicationService.putFile(file , request.getRemoteAddr());
    }

    /**
     * This mapping will put a file to local storage and use A GIVEN IP as
     * reference of where the file came from
     *
     * @param file file to save to node
     * @param ipOfRef given ip as refrence
     * @return
     */
    @PostMapping("/file/{ipOfRef}")
    public ResponseEntity<String> uploadFileGivenIP(@RequestParam("file") MultipartFile file  , @PathVariable String ipOfRef)  {
        return replicationService.putFile(file , ipOfRef );
    }




    @PostMapping("/id/next")
    public ResponseEntity<String> updateNextNode(@RequestBody Node nextNode)  {
        logger.info("POST: /id/next/" + nextNode);
        nodeService.setNextNode(nextNode);
        return ResponseEntity.ok("NextNode updated succesfully");

    }

    @PostMapping("/id/previous")
    public ResponseEntity<String> updatePreviousNode(@RequestBody Node previousNode)  {
        logger.info("POST: /id/previous/" + previousNode);
        nodeService.setPreviousNode(previousNode);
        return ResponseEntity.ok("PreviousNode updated succesfully");

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


    @PutMapping("/reference/localGone")
    public ResponseEntity<String> iHaveYourReplicateAndYouDontExistAnymore(@RequestBody ReferenceDTO referenceDTO) {
        return replicationService.iHaveYourReplicateAndYouDontExistAnymore(referenceDTO.getFileName() , referenceDTO.getIpOfReference());

    }
    @PutMapping("/reference/referenceGone")
    public ResponseEntity<String> iHaveLocalFileAndReplicationIsGone(@RequestBody ReferenceDTO referenceDTO) {
        System.out.println("iHaveLocalFileAndReplicationIsGone");
        System.out.println(referenceDTO.getFileName());
        System.out.println(referenceDTO.getIpOfReference());
        return replicationService.iHaveLocalFileAndReplicationIsGone(referenceDTO.getFileName() , referenceDTO.getIpOfReference());

    }

    @GetMapping("/agent/fileList")
    public ResponseEntity<List<LocalFile>> getAgentFileList() {
        return nodeService.getAgentFileList();
    }

    // TODO: temporary test mapping
    @PostMapping("/agent/sendLockNotification")
    public ResponseEntity<String> sendLockNotification(@RequestBody LocalFile localFile) {
        nodeService.sendLockNotification(localFile.getFileName(), localFile.isLocked());
        return new ResponseEntity<>(localFile.getFileName() + " locked by Agent", HttpStatus.OK);
    }
}
