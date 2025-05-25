package ds.namingnote.Controller;

import ds.namingnote.Service.NodeService;
import ds.namingnote.Service.ReplicationService;
import ds.namingnote.Utilities.NextAndPreviousNodeDTO;
import ds.namingnote.Utilities.Node;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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


    /**
     * This endpoint will get a file from the node
     *
     * @param filename the name of the file asked
     * @param request request to get senders ip
     * @return
     */
    @GetMapping("/file/{filename}")
    public ResponseEntity<Resource> returnFile(@PathVariable String filename, HttpServletRequest request) {
        return replicationService.getFile(filename, request.getRemoteAddr()); // get the file
    }



    /**
     * This endpoint will get a file from the node
     * Used by naming server to forward a request from somewhere
     *
     * @param filename the name of the file asked
     * @param requesterIP the ip of the machine that requested the file
     * @return
     */
    @GetMapping("/file/with-requesterIP")
    public ResponseEntity<Resource> returnFileWithRequester(@RequestParam String filename, @RequestParam String requesterIP) {
        return replicationService.getFile(filename, requesterIP); // get the file
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
        System.out.println("File Upload requested from ip : " + request.getRemoteAddr());
        return replicationService.putFile(file , request.getRemoteAddr());
    }

    /**
     * This mapping will put a file to local storage and use A GIVEN IP as
     * reference of where the file came from
     *
     * @param file file to save to node
     * @param ipOfRef Given ip as reference
     *                If this reference is the same as the node that receives this -> this node will be new owner,
     *                this is part of the failure agent.
     * @return
     */
    @PostMapping("/file/{ipOfRef}")
    public ResponseEntity<String> uploadFileGivenIP(@RequestParam("file") MultipartFile file  , @PathVariable String ipOfRef)  {
        System.out.println("File upload requested with refrence ip : " + ipOfRef);
        return replicationService.putFile(file , ipOfRef );
    }


    /**
     * Set the next node of the node this is called on
     *
     * @param nextNode : what node should be set as the next node
     * @return
     */
    @PostMapping("/id/next")
    public ResponseEntity<String> setNextNode(@RequestBody Node nextNode)  {
        logger.info("POST: /id/next/" + nextNode);
        nodeService.setNextNode(nextNode);
        return ResponseEntity.ok("NextNode updated succesfully");

    }

    /**
     * Set the next node of the node this is called on
     *
     * @param previousNode : what node should be set as the next node
     * @return
     */
    @PostMapping("/id/previous")
    public ResponseEntity<String> setPreviousNode(@RequestBody  Node previousNode)  {
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
    public ResponseEntity<String> shutdown(){
        nodeService.shutdown();
        return ResponseEntity.ok("Node stopped successfully!");
    }

    @PostMapping("/start")
    public ResponseEntity<String> start() {
        nodeService.startProcessing();
        return ResponseEntity.ok("Node started successfully!");
    }


    @GetMapping(value = "/nextAndPrevious", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NextAndPreviousNodeDTO> getNextAndPrevious() {
        NextAndPreviousNodeDTO nextAndPrevious = nodeService.getNextAndPrevious();
        return ResponseEntity.ok(nextAndPrevious);
    }
}
