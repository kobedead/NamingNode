package ds.namingnote.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ds.namingnote.model.FileReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileReferenceJsonHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File jsonFile;

    public FileReferenceJsonHandler(String filePath) {
        this.jsonFile = new File(filePath);
    }

    public void saveFileReference(FileReference fileReference) throws IOException {
        List<FileReference> fileReferences = loadAllFileReferences();
        fileReferences.removeIf(fr -> fr.getFileName().equals(fileReference.getFileName())); // Remove old if exists
        fileReferences.add(fileReference);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, fileReferences);
    }

    public List<FileReference> loadAllFileReferences() throws IOException {
        if (!jsonFile.exists()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(jsonFile, new TypeReference<List<FileReference>>() {});
    }

    public FileReference getFileReferenceByFileName(String fileName) throws IOException {
        List<FileReference> fileReferences = loadAllFileReferences();
        for (FileReference fr : fileReferences) {
            if (fr.getFileName().equals(fileName)) {
                return fr;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File reference not found");
    }
}
