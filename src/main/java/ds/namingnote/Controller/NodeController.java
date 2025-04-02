package ds.namingnote.Controller;

import ds.namingnote.Service.NodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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







}
