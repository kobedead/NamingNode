package ds.namingnote.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

@RestController
@RequestMapping("/node")
public class NodeController {





    @GetMapping("/file/{filename}")
    public ResponseEntity getNodeIP(@PathVariable String filename) throws FileNotFoundException {

        // Checking whether the file requested for download exists or not
        //String fileUploadpath = System.getProperty("user.dir") +"/Uploads";
        //String[] filenames = this.getFiles();
        //boolean contains = Arrays.asList(filenames).contains(filename);
        //if(!contains) {
        //    return new ResponseEntity("FIle Not Found",HttpStatus.NOT_FOUND);
        //}

        // Setting up the filepath
        String filePath = "D:/schoolshit/6_DS/Lab3_node/NamingNote/yea";

        // Creating new file instance
        File file= new File(filePath);
        // Creating a new InputStreamResource object
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));

        // Creating a new instance of HttpHeaders Object
        HttpHeaders headers = new HttpHeaders();

        // Setting up values for contentType and headerValue
        String contentType = "application/octet-stream";
        String headerValue = "attachment; filename=\"" + resource.getFilename() + "\"";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .body(resource);

    }








}
