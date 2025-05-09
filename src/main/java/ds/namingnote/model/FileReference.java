package ds.namingnote.model;

import java.util.ArrayList;
import java.util.List;


/**
 * For every file that a node contains, a FileReference is created
 * This FileReference describes for a specific fileName to which nodes it has been replicated
 */
public class FileReference {
    String fileName;
    List<String> replicationNodeIP;

    public FileReference(String fileName) {
        this.fileName = fileName;
        replicationNodeIP = new ArrayList<String>();
    }

    public String getFileName() {
        return fileName;
    }

    public List<String> getReplicationNodeIP() {
        return replicationNodeIP;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setReplicationNodeIP(List<String> replicationNodeIP) {
        this.replicationNodeIP = replicationNodeIP;
    }
}
